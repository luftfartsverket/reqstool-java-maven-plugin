// Copyright © LFV
package se.lfv.reqstool.plugins.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

@Mojo(name = "attach-reqstool-zip", defaultPhase = LifecyclePhase.PACKAGE)
public class RequirementsToolMojo extends AbstractMojo {

	public static final String TEST_RESULTS_SUREFIRE = "test_results/surefire";

	public static final String MANUAL_VERIFICATION_RESULTS_YML = "manual_verification_results.yml";

	public static final String SOFTWARE_VERIFICATION_CASES_YML = "software_verification_cases.yml";

	public static final String REQUIREMENTS_YML = "requirements.yml";

	public static final String ANNOTATIONS_YML = "annotations.yml";

	public static final String TESTS = "tests";

	public static final String IMPLEMENTATIONS = "implementations";

	public static final String REQUIREMENT_ANNOTATIONS = "requirement_annotations";

	public static final String TEST_RESULTS_FAILSAFE = "test_results/failsafe";

	public static final String REQSTOOL_DIRECTORY = "";

	protected static final String YAML_LANG_SERVER_SCHEMA_INFO = "# yaml-language-server: $schema=https://raw.githubusercontent.com/Luftfartsverket/reqstool-client/main/src/reqstool/resources/schemas/v1/annotations.schema.json";

	protected static final ObjectMapper yamlMapper;

	static {
		yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR));
		yamlMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	@Parameter(property = "requirementsAnnotationsFile",
			defaultValue = "${project.build.directory}/generated-sources/annotations/resources/annotations.yml")
	private File requirementsAnnotationsFile;

	@Parameter(property = "svcsAnnotationsFile",
			defaultValue = "${project.build.directory}/generated-test-sources/test-annotations/resources/annotations.yml")
	private File svcsAnnotationsFile;

	@Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/reqstool")
	private File outputDirectory;

	@Parameter(property = "datasetPath", defaultValue = "${project.basedir}/reqstool")
	private File datasetPath;

	@Parameter(property = "failsafeReportsDir", defaultValue = "${project.build.directory}/failsafe-reports")
	private File failsafeReportsDir;

	@Parameter(property = "surefireReportsDir", defaultValue = "${project.build.directory}/surefire-reports")
	private File surefireReportsDir;

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	@Component
	private MavenProjectHelper projectHelper;

	private String zipFilename;

	@Parameter(defaultValue = "${log}", readonly = true)
	private Log log;

	public void execute() throws MojoExecutionException {
		getLog().debug("Creating Reqstool Maven zip artifact");

		zipFilename = project.getBuild().getFinalName() + "-reqstool" + ".zip";

		JsonNode implementationsNode = yamlMapper.createObjectNode();
		JsonNode testsNode = yamlMapper.createObjectNode();

		try {
			if (requirementsAnnotationsFile.exists()) {
				implementationsNode = yamlMapper.readTree(requirementsAnnotationsFile)
					.path(REQUIREMENT_ANNOTATIONS)
					.path(IMPLEMENTATIONS);
			}

			if (svcsAnnotationsFile.exists()) {
				testsNode = yamlMapper.readTree(svcsAnnotationsFile).path(REQUIREMENT_ANNOTATIONS).path(TESTS);
			}

			JsonNode combinedOutputNode = combineOutput(implementationsNode, testsNode);

			if (!outputDirectory.exists()) {
				outputDirectory.mkdirs();
			}

			writeCombinedOutputToFile(new File(outputDirectory, ANNOTATIONS_YML), combinedOutputNode);

			createZipFile();
			attachArtifact();

		}
		catch (IOException e) {
			throw new MojoExecutionException("Error combining annotations or creating zip file", e);
		}
	}

	static JsonNode combineOutput(JsonNode implementationsNode, JsonNode testsNode) {

		// Create an ObjectNode for "requirement_annotations"
		ObjectNode requirementAnnotationsNode = yamlMapper.createObjectNode();
		if (!implementationsNode.isEmpty()) {
			requirementAnnotationsNode.set(IMPLEMENTATIONS, implementationsNode);
		}
		if (!testsNode.isEmpty()) {
			requirementAnnotationsNode.set(TESTS, testsNode);
		}

		ObjectNode newNode = yamlMapper.createObjectNode();
		newNode.set(REQUIREMENT_ANNOTATIONS, requirementAnnotationsNode);

		return newNode;
	}

	static void writeCombinedOutputToFile(File outputFile, JsonNode combinedOutputNode) throws IOException {

		try (Writer writer = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {

			// Write the YAML language server schema info as a comment
			writer.write(YAML_LANG_SERVER_SCHEMA_INFO + System.lineSeparator());

			// Write the JSON node data
			yamlMapper.writeValue(writer, combinedOutputNode);
		}
	}

	private void createZipFile() throws IOException {
		File zipFile = new File(outputDirectory, zipFilename);
		getLog().info("Creating zip file: " + zipFile.getAbsolutePath());

		try (FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zipOut = new ZipOutputStream(fos)) {

			addFileToZip(zipOut, new File(datasetPath, REQUIREMENTS_YML), null);
			addFileToZip(zipOut, new File(datasetPath, SOFTWARE_VERIFICATION_CASES_YML), null);
			addFileToZip(zipOut, new File(datasetPath, MANUAL_VERIFICATION_RESULTS_YML), null);
			addFileToZip(zipOut, new File(outputDirectory, ANNOTATIONS_YML), null);

			addXmlFilesToZip(zipOut, failsafeReportsDir, new File(TEST_RESULTS_FAILSAFE));
			addXmlFilesToZip(zipOut, surefireReportsDir, new File(TEST_RESULTS_SUREFIRE));
		}
	}

	private void addXmlFilesToZip(ZipOutputStream zipOut, File directory, File targetDirectory) throws IOException {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					addXmlFilesToZip(zipOut, file, new File(targetDirectory, file.toString()));
				}
				else if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".xml")) {
					addFileToZip(zipOut, file, targetDirectory);
				}
			}
		}
	}

	private void addFileToZip(ZipOutputStream zipOut, File file, File targetDirectory) throws IOException {
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
		File zipFile = new File(outputDirectory, zipFilename);

		getLog().info("Attaching artifact: " + zipFile.getName());

		projectHelper.attachArtifact(project, "zip", "reqstool", zipFile);
	}

}
