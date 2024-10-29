// Copyright © LFV
package se.lfv.reqstool.plugins.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;

class RequirementsToolMojoTests {

	@Test
	void testCombine() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();

		File implementationsAnnotationsFile = new File(
				classLoader.getResource("yml/requirements_annotations.yml").getFile());
		File testsAnnotationsFile = new File(classLoader.getResource("yml/svcs_annotations.yml").getFile());

		JsonNode implementationsNode = RequirementsToolMojo.yamlMapper.readTree(implementationsAnnotationsFile)
			.path(RequirementsToolMojo.XML_REQUIREMENT_ANNOTATIONS)
			.path(RequirementsToolMojo.XML_IMPLEMENTATIONS);

		JsonNode testsNode = RequirementsToolMojo.yamlMapper.readTree(testsAnnotationsFile)
			.path(RequirementsToolMojo.XML_REQUIREMENT_ANNOTATIONS)
			.path(RequirementsToolMojo.XML_TESTS);

		JsonNode combinedNode = RequirementsToolMojo.combineOutput(implementationsNode, testsNode);

		String combinedResult = RequirementsToolMojo.yamlMapper.writeValueAsString(combinedNode);

		File combinedAnnotationsFile = new File(classLoader.getResource("yml/combined_annotations.yml").getFile());

		byte[] combinedFileContent = Files.readAllBytes(combinedAnnotationsFile.toPath());
		String combinedFileContentAsString = new String(combinedFileContent);

		assertEquals(combinedFileContentAsString, combinedResult, "The combined annotations files should match");
	}

	@Test
	void testAssembleZipArtifact() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		URL resourcePath = classLoader.getResource("zip");
		Path zipResourcePath = Paths.get(resourcePath.getFile());

		RequirementsToolMojo mojo = new RequirementsToolMojo();

		// Create and set a mock MavenProject
		MavenProject mockProject = new MavenProject();
		Build build = new Build();
		build.setFinalName("test-project");
		mockProject.setBuild(build);

		// Set all required fields
		setFieldValue(mojo, "project", mockProject);
		setFieldValue(mojo, "outputDirectory", zipResourcePath.toFile());
		setFieldValue(mojo, "datasetPath", zipResourcePath.toFile());
		setFieldValue(mojo, "failsafeReportsDir", zipResourcePath.toFile());
		setFieldValue(mojo, "surefireReportsDir", zipResourcePath.toFile());
		setFieldValue(mojo, "requirementsAnnotationsFile", createTestFile(zipResourcePath, "requirements.yml"));
		setFieldValue(mojo, "svcsAnnotationsFile", createTestFile(zipResourcePath, "software_verification_cases.yml"));
		setFieldValue(mojo, "mvrsAnnotationsFile", createTestFile(zipResourcePath, "manual_verification_results.yml"));
		setFieldValue(mojo, "annotationsFile", createTestFile(zipResourcePath, "annotations.yml"));

		// Invoke the method
		Method assembleZipArtifactMethod = RequirementsToolMojo.class.getDeclaredMethod("assembleZipArtifact");
		assembleZipArtifactMethod.setAccessible(true);
		assembleZipArtifactMethod.invoke(mojo);

		assertTrue(Files.exists(zipResourcePath.resolve("test-project-reqstool.zip")));
	}

	// Helper method to set field values
	private void setFieldValue(Object object, String fieldName, Object value) throws Exception {
		Field field = RequirementsToolMojo.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(object, value);
	}

	// Helper method to create test files
	private File createTestFile(Path directory, String filename) throws IOException {
		File file = new File(directory.toFile(), filename);
		if (!file.exists()) {
			file.getParentFile().mkdirs();
			file.createNewFile();
			// Optionally write some content to the file
			Files.write(file.toPath(), "test content".getBytes());
		}
		return file;
	}

}
