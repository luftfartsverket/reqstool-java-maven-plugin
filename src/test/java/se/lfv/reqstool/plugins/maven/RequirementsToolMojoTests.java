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

		// Set the basedir in MavenProject using reflection
		Field basedirField = mockProject.getClass().getDeclaredField("basedir");
		basedirField.setAccessible(true);
		basedirField.set(mockProject, zipResourcePath.toFile());

		// Set up all required fields
		Field projectField = RequirementsToolMojo.class.getDeclaredField("project");
		Field outputDirectoryField = RequirementsToolMojo.class.getDeclaredField("outputDirectory");
		Field datasetPathField = RequirementsToolMojo.class.getDeclaredField("datasetPath");
		Field testResultsField = RequirementsToolMojo.class.getDeclaredField("testResults");

		projectField.setAccessible(true);
		outputDirectoryField.setAccessible(true);
		datasetPathField.setAccessible(true);
		testResultsField.setAccessible(true);

		projectField.set(mojo, mockProject);
		outputDirectoryField.set(mojo, zipResourcePath.toFile());
		datasetPathField.set(mojo, zipResourcePath.toFile());
		testResultsField.set(mojo, new String[] { "test_results/**/*.xml" });

		// Create mandatory requirements.yml file
		File reqFile = new File(zipResourcePath.toFile(), "requirements.yml");
		reqFile.getParentFile().mkdirs();
		reqFile.createNewFile();
		Files.write(reqFile.toPath(), "test content".getBytes());

		// Invoke the method
		Method assembleZipArtifactMethod = RequirementsToolMojo.class.getDeclaredMethod("assembleZipArtifact");
		assembleZipArtifactMethod.setAccessible(true);
		assembleZipArtifactMethod.invoke(mojo);

		// Only check if zip file exists
		assertTrue(Files.exists(zipResourcePath.resolve("test-project-reqstool.zip")));
	}

}
