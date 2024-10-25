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
		String zipArtifactFilename = "reqstool-test-zip";
		String projectReqstoolDir = "test-project-reqstool";

		RequirementsToolMojo mojo = new RequirementsToolMojo();

		Field outputDirectoryField = RequirementsToolMojo.class.getDeclaredField("outputDirectory");
		Field datasetPathField = RequirementsToolMojo.class.getDeclaredField("datasetPath");
		Field zipArtifactFilenameField = RequirementsToolMojo.class.getDeclaredField("zipArtifactFilename");
		Field failsafeReportsDirField = RequirementsToolMojo.class.getDeclaredField("failsafeReportsDir");
		Field surefireReportsDirField = RequirementsToolMojo.class.getDeclaredField("surefireReportsDir");
		Field projectReqstoolDirField = RequirementsToolMojo.class.getDeclaredField("projectReqstoolDir");

		outputDirectoryField.setAccessible(true);
		datasetPathField.setAccessible(true);
		zipArtifactFilenameField.setAccessible(true);
		failsafeReportsDirField.setAccessible(true);
		surefireReportsDirField.setAccessible(true);
		projectReqstoolDirField.setAccessible(true);

		outputDirectoryField.set(mojo, zipResourcePath.toFile());
		datasetPathField.set(mojo, zipResourcePath.toFile());
		failsafeReportsDirField.set(mojo, zipResourcePath.toFile());
		surefireReportsDirField.set(mojo, zipResourcePath.toFile());
		zipArtifactFilenameField.set(mojo, zipArtifactFilename);
		projectReqstoolDirField.set(mojo, projectReqstoolDir);
		// Or whatever test value you want to use

		Method assembleZipArtifactMethod = RequirementsToolMojo.class.getDeclaredMethod("assembleZipArtifact");
		assembleZipArtifactMethod.setAccessible(true);

		assembleZipArtifactMethod.invoke(mojo);

		assertTrue(Files.exists(zipResourcePath.resolve(zipArtifactFilename)));

	}

}
