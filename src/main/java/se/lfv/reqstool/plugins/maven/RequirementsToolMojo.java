// Copyright © LFV
package se.lfv.reqstool.plugins.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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

	private static final String TEST_RESULTS_PATTERN = "test_results/**/*.xml";

	public static final String INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML = "manual_verification_results.yml";

	public static final String INPUT_FILE_REQUIREMENTS_YML = "requirements.yml";

	public static final String INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML = "software_verification_cases.yml";

	public static final String INPUT_PATH_DATASET = "reqstool";

	public static final String OUTPUT_FILE_ANNOTATIONS_YML_FILE = "annotations.yml";

	private static final String OUTPUT_ARTIFACT_CLASSIFIER = "reqstool";

	public static final String OUTPUT_ARTIFACT_FILE_REQSTOOL_CONFIG_YML = "reqstool_config.yml";

	public static final String OUTPUT_ARTIFACT_DIR_TEST_RESULTS = "test_results";

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

	@Parameter(property = "reqstool.outputDirectory", defaultValue = "${project.build.directory}/reqstool")
	private File outputDirectory;

	@Parameter(property = "reqstool.annotationsFile",
			defaultValue = "${project.build.directory}/reqstool/annotations.yml")
	private File annotationsFile;

	@Parameter(property = "reqstool.datasetPath", defaultValue = "${project.basedir}/docs/reqstool")
	private File datasetPath;

	@Parameter(property = "reqstool.testResults")
	private String[] testResults;

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
		getLog().info(Arrays.toString(testResults));
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

	private void assembleZipArtifact() throws IOException, MojoExecutionException {
		String zipArtifactFilename = project.getBuild().getFinalName() + "-reqstool.zip";
		String topLevelDir = project.getBuild().getFinalName() + "-reqstool";

		File zipFile = new File(outputDirectory, zipArtifactFilename);
		getLog().info("Assembling zip file: " + zipFile.getAbsolutePath());

		try (FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zipOut = new ZipOutputStream(fos)) {

			Map<String, String> reqstoolConfigResources = new HashMap<>();

			File requirementsFile = new File(datasetPath, INPUT_FILE_REQUIREMENTS_YML);
			if (!requirementsFile.isFile()) {
				String msg = "Missing mandatory " + INPUT_FILE_REQUIREMENTS_YML + ": "
						+ requirementsFile.getAbsolutePath();
				throw new MojoExecutionException(msg);
			}

			addFileToZipArtifact(zipOut, requirementsFile, new File(topLevelDir));
			getLog().debug("added to " + topLevelDir + ": " + requirementsFile);
			reqstoolConfigResources.put("requirements", INPUT_FILE_REQUIREMENTS_YML);

			File svcsFile = new File(datasetPath, INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML);
			if (svcsFile.isFile()) {
				addFileToZipArtifact(zipOut, svcsFile, new File(topLevelDir));
				getLog().debug("added to " + topLevelDir + ": " + svcsFile);
				reqstoolConfigResources.put("software_verification_cases", INPUT_FILE_SOFTWARE_VERIFICATION_CASES_YML);
			}
			File mvrsFile = new File(datasetPath, INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML);
			if (mvrsFile.isFile()) {
				addFileToZipArtifact(zipOut, mvrsFile, new File(topLevelDir));
				getLog().debug("added to " + topLevelDir + ": " + mvrsFile);
				reqstoolConfigResources.put("manual_verification_results", INPUT_FILE_MANUAL_VERIFICATION_RESULTS_YML);
			}
			File annotationsZipFile = new File(outputDirectory, OUTPUT_FILE_ANNOTATIONS_YML_FILE);
			if (annotationsZipFile.isFile()) {
				addFileToZipArtifact(zipOut, annotationsZipFile, new File(topLevelDir));
				getLog().debug("added to " + topLevelDir + ": " + annotationsZipFile);
				reqstoolConfigResources.put("annotations", OUTPUT_FILE_ANNOTATIONS_YML_FILE);
			}

			Path dir = Paths.get(project.getBasedir().toURI());
			List<String> patterns = Arrays.stream(testResults)
				.map(pattern -> "glob:" + pattern)
				.collect(Collectors.toList());

			List<PathMatcher> matchers = patterns.stream()
				.map(pattern -> FileSystems.getDefault().getPathMatcher(pattern))
				.collect(Collectors.toList());

			AtomicInteger testResultsCount = new AtomicInteger(0);

			Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path relativePath = dir.relativize(file);
					getLog().debug("Checking file: " + relativePath);

					if (matchers.stream().anyMatch(matcher -> matcher.matches(relativePath))) {
						getLog().debug("Match found for: " + relativePath);
						addFileToZipArtifact(zipOut, file.toFile(),
								new File(topLevelDir, OUTPUT_ARTIFACT_DIR_TEST_RESULTS));
						testResultsCount.incrementAndGet();
					}
					return FileVisitResult.CONTINUE;
				}
			});

			getLog().debug("testResults values: " + Arrays.toString(testResults));
			getLog().debug("added " + testResultsCount + " test_results");
			getLog().debug("added test results: " + TEST_RESULTS_PATTERN);
			reqstoolConfigResources.put("test_results", TEST_RESULTS_PATTERN);

			addReqstoolConfigYamlToZip(zipOut, new File(topLevelDir), reqstoolConfigResources);
		}

		getLog().info("Assembled zip artifact: " + zipFile.getAbsolutePath());

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
		projectHelper.attachArtifact(project, "zip", OUTPUT_ARTIFACT_CLASSIFIER, zipFile);
	}

	private void addReqstoolConfigYamlToZip(ZipOutputStream zipOut, File topLevelDir,
			Map<String, String> reqstoolConfigResources) throws IOException {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);

		LinkedHashMap<String, Object> yamlData = new LinkedHashMap<>();
		yamlData.put("language", "java");
		yamlData.put("build", "maven");

		yamlData.put("resources", reqstoolConfigResources);

		ZipEntry zipEntry = new ZipEntry(new File(topLevelDir, OUTPUT_ARTIFACT_FILE_REQSTOOL_CONFIG_YML).toString());
		zipOut.putNextEntry(zipEntry);

		Writer writer = new OutputStreamWriter(zipOut, StandardCharsets.UTF_8);
		writer.write(String.format("%s%n", YAML_LANG_SERVER_SCHEMA_CONFIG));
		writer.write(String.format("# version: %s%n", project.getVersion()));
		yaml.dump(yamlData, writer);
		writer.flush();

		zipOut.closeEntry();
	}

}
