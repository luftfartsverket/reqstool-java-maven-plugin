// Copyright © LFV
package se.lfv.reqstool.plugins.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

@Mojo(name = "assemble-and-attach-zip-artifact", defaultPhase = LifecyclePhase.PACKAGE)
public class RequirementsToolMojo extends AbstractMojo {

	// Constants

	public static final String INPUT_DIR_TEST_RESULTS_FAILSAFE = "test_results/failsafe";

	public static final String INPUT_DIR_TEST_RESULTS_SUREFIRE = "test_results/surefire";

	public static final String INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML = "manual_verification_results.yml";

	public static final String INPUT_FILE_REQUIREMENTS_YML = "requirements.yml";

	public static final String INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML = "software_verification_cases.yml";

	public static final String INPUT_PATH_DATASET = "reqstool";

	public static final String OUTPUT_FILE_REQSTOOL_CONFIG_YML = "reqstool_config.yml";

	public static final String OUTPUT_FILE_ANNOTATIONS_YML_FILE = "annotations.yml";

	public static final String XML_IMPLEMENTATIONS = "implementations";

	public static final String XML_REQUIREMENT_ANNOTATIONS = "requirement_annotations";

	public static final String XML_TESTS = "tests";

	protected static final String YAML_LANG_SERVER_SCHEMA_ANNOTATIONS = "# yaml-language-server: $schema=https://raw.githubusercontent.com/Luftfartsverket/reqstool-client/main/src/reqstool/resources/schemas/v1/annotations.schema.json";

	protected static final String YAML_LANG_SERVER_SCHEMA_CONFIG = "# yaml-language-server: $schema=https://raw.githubusercontent.com/Luftfartsverket/reqstool-client/main/src/reqstool/resources/schemas/v1/reqstool_config.schema.json";

	protected static final ObjectMapper yamlMapper;

	static {
		yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR));
		yamlMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	@Parameter(property = "reqstool.requirementsAnnotationsFile",
			defaultValue = "${project.build.directory}/generated-sources/annotations/resources/annotations.yml")
	private File requirementsAnnotationsFile;

	@Parameter(property = "reqstool.svcsAnnotationsFile",
			defaultValue = "${project.build.directory}/generated-test-sources/test-annotations/resources/annotations.yml")
	private File svcsAnnotationsFile;

	@Parameter(property = "reqstool.mvrsAnnotationsFile",
			defaultValue = "${project.basedir}/docs/reqstool/manual_verification_results.yml")
	private File mvrsAnnotationsFile;

	@Parameter(property = "reqstool.outputDirectory", defaultValue = "${project.build.directory}/reqstool")
	private File outputDirectory;

	@Parameter(property = "reqstool.annotationsFile",
			defaultValue = "${project.build.directory}/reqstool/annotations.yml")
	private File annotationsFile;

	@Parameter(property = "reqstool.datasetPath", defaultValue = "${project.basedir}/docs/reqstool")
	private File datasetPath;

	@Parameter(property = "reqstool.failsafeReportsDir", defaultValue = "${project.build.directory}/failsafe-reports")
	private File failsafeReportsDir;

	@Parameter(property = "reqstool.surefireReportsDir", defaultValue = "${project.build.directory}/surefire-reports")
	private File surefireReportsDir;

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	@Inject
	private MavenProjectHelper projectHelper;

	@Parameter(defaultValue = "${log}", readonly = true)
	private Log log;

	@Parameter(property = "reqstool.skip", defaultValue = "false")
	private boolean skip;

	@Parameter(property = "reqstool.skipAssembleZipArtifact", defaultValue = "false")
	private boolean skipAssembleZipArtifact;

	@Parameter(property = "reqstool.skipAttachZipArtifact", defaultValue = "false")
	private boolean skipAttachZipArtifact;

	public void execute() throws MojoExecutionException {
		if (skip) {
			getLog().info("Skipping execution of reqstool plugin");
			return;
		}

		getLog().debug("Assembling and Attaching Reqstool Maven Zip Artifact");

		try {

			JsonNode implementationsNode = yamlMapper.createObjectNode();
			JsonNode testsNode = yamlMapper.createObjectNode();

			if (requirementsAnnotationsFile.exists()) {
				implementationsNode = yamlMapper.readTree(requirementsAnnotationsFile)
					.path(XML_REQUIREMENT_ANNOTATIONS)
					.path(XML_IMPLEMENTATIONS);
			}

			if (svcsAnnotationsFile.exists()) {
				testsNode = yamlMapper.readTree(svcsAnnotationsFile).path(XML_REQUIREMENT_ANNOTATIONS).path(XML_TESTS);
			}

			JsonNode combinedOutputNode = combineOutput(implementationsNode, testsNode);

			if (!outputDirectory.exists()) {
				outputDirectory.mkdirs();
			}

			writeCombinedOutputToFile(new File(outputDirectory, OUTPUT_FILE_ANNOTATIONS_YML_FILE), combinedOutputNode);

			if (!skipAssembleZipArtifact) {
				assembleZipArtifact();
			}
			else {
				getLog().info("Skipping zip artifact assembly");
			}

			if (!skipAttachZipArtifact) {
				attachArtifact();
			}
			else {
				getLog().info("Skipping zip artifact attachment");
			}

		}
		catch (IOException e) {
			throw new MojoExecutionException("Error combining annotations or creating zip file", e);
		}
	}

	static JsonNode combineOutput(JsonNode implementationsNode, JsonNode testsNode) {
		ObjectNode requirementAnnotationsNode = yamlMapper.createObjectNode();
		if (!implementationsNode.isEmpty()) {
			requirementAnnotationsNode.set(XML_IMPLEMENTATIONS, implementationsNode);
		}
		if (!testsNode.isEmpty()) {
			requirementAnnotationsNode.set(XML_TESTS, testsNode);
		}

		ObjectNode newNode = yamlMapper.createObjectNode();
		newNode.set(XML_REQUIREMENT_ANNOTATIONS, requirementAnnotationsNode);

		return newNode;
	}

	private void writeCombinedOutputToFile(File outputFile, JsonNode combinedOutputNode) throws IOException {
		getLog().info("Combining " + requirementsAnnotationsFile + " and " + svcsAnnotationsFile + " into "
				+ outputFile.getAbsolutePath());

		try (Writer writer = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
			writer.write(YAML_LANG_SERVER_SCHEMA_ANNOTATIONS + System.lineSeparator());
			yamlMapper.writeValue(writer, combinedOutputNode);
		}
	}

	private void assembleZipArtifact() throws IOException {
		String zipArtifactFilename = project.getBuild().getFinalName() + "-reqstool.zip";
		String topLevelDir = project.getBuild().getFinalName() + "-reqstool";

		File zipFile = new File(outputDirectory, zipArtifactFilename);
		getLog().info("Assembling zip file: " + zipFile.getAbsolutePath());

		try (FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zipOut = new ZipOutputStream(fos)) {

			addFileToZipArtifact(zipOut, new File(datasetPath, INPUT_FILE_REQUIREMENTS_YML), new File(topLevelDir));
			addFileToZipArtifact(zipOut, new File(datasetPath, INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML),
					new File(topLevelDir));
			addFileToZipArtifact(zipOut, new File(datasetPath, INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML),
					new File(topLevelDir));
			addFileToZipArtifact(zipOut, new File(outputDirectory, OUTPUT_FILE_ANNOTATIONS_YML_FILE),
					new File(topLevelDir));

			addReqtoolConfigYamlToZip(zipOut, new File(topLevelDir));

			addXmlFilesToZipArtifact(zipOut, failsafeReportsDir,
					new File(topLevelDir, INPUT_DIR_TEST_RESULTS_FAILSAFE));
			addXmlFilesToZipArtifact(zipOut, surefireReportsDir,
					new File(topLevelDir, INPUT_DIR_TEST_RESULTS_SUREFIRE));
		}

		getLog().info("Assembled zip artifact: " + zipFile.getAbsolutePath());

	}

	private void addXmlFilesToZipArtifact(ZipOutputStream zipOut, File directory, File targetDirectory)
			throws IOException {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					addXmlFilesToZipArtifact(zipOut, file, new File(targetDirectory, file.toString()));
				}
				else if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".xml")) {
					addFileToZipArtifact(zipOut, file, targetDirectory);
				}
			}
		}
	}

	private void addFileToZipArtifact(ZipOutputStream zipOut, File file, File targetDirectory) throws IOException {
		if (file.exists()) {
			File entryName;
			if (targetDirectory == null || targetDirectory.getName().isEmpty()) {
				entryName = new File(file.getName());
			}
			else {
				entryName = new File(targetDirectory, file.getName());
			}

			getLog().info("Adding file: " + entryName.toString());

			ZipEntry zipEntry = new ZipEntry(entryName.toString());
			zipOut.putNextEntry(zipEntry);

			byte[] bytes = FileUtils.readFileToByteArray(file);
			zipOut.write(bytes, 0, bytes.length);
			zipOut.closeEntry();
		}
	}

	private void attachArtifact() {
		String zipArtifactFilename = project.getBuild().getFinalName() + "-reqstool.zip";
		File zipFile = new File(outputDirectory, zipArtifactFilename);
		getLog().info("Attaching artifact: " + zipFile.getName());
		projectHelper.attachArtifact(project, "zip", "reqstool", zipFile);
	}

	private void addReqtoolConfigYamlToZip(ZipOutputStream zipOut, File topLevelDir) throws IOException {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);

		LinkedHashMap<String, Object> yamlData = new LinkedHashMap<>();
		yamlData.put("language", "java");
		yamlData.put("build", "maven");

		LinkedHashMap<String, Object> resources = new LinkedHashMap<>();
		if (requirementsAnnotationsFile.exists()) {
			resources.put("requirements", INPUT_FILE_REQUIREMENTS_YML);
		}
		if (svcsAnnotationsFile.exists()) {
			resources.put("software_verification_cases", INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML);
		}
		if (mvrsAnnotationsFile.exists()) {
			resources.put("manual_verification_results", INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML);
		}
		if (annotationsFile.exists()) {
			resources.put("annotations", OUTPUT_FILE_ANNOTATIONS_YML_FILE);
		}

		resources.put("test_results", Arrays.asList(INPUT_DIR_TEST_RESULTS_FAILSAFE, INPUT_DIR_TEST_RESULTS_SUREFIRE));

		yamlData.put("resources", resources);

		ZipEntry zipEntry = new ZipEntry(new File(topLevelDir, OUTPUT_FILE_REQSTOOL_CONFIG_YML).toString());
		zipOut.putNextEntry(zipEntry);

		Writer writer = new OutputStreamWriter(zipOut, StandardCharsets.UTF_8);
		writer.write(String.format("%s%n", YAML_LANG_SERVER_SCHEMA_CONFIG));
		writer.write(String.format("# version: %s%n", project.getVersion()));
		yaml.dump(yamlData, writer);
		writer.flush();

		zipOut.closeEntry();
	}

}
