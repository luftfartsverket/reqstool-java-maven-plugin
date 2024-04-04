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
			.path(RequirementsToolMojo.REQUIREMENT_ANNOTATIONS)
			.path(RequirementsToolMojo.IMPLEMENTATIONS);

		JsonNode testsNode = RequirementsToolMojo.yamlMapper.readTree(testsAnnotationsFile)
			.path(RequirementsToolMojo.REQUIREMENT_ANNOTATIONS)
			.path(RequirementsToolMojo.TESTS);

		JsonNode combinedNode = RequirementsToolMojo.combineOutput(implementationsNode, testsNode);

		String combinedResult = RequirementsToolMojo.yamlMapper.writeValueAsString(combinedNode);

		File combinedAnnotationsFile = new File(classLoader.getResource("yml/combined_annotations.yml").getFile());

		byte[] combinedFileContent = Files.readAllBytes(combinedAnnotationsFile.toPath());
		String combinedFileContentAsString = new String(combinedFileContent);

		assertEquals(combinedFileContentAsString, combinedResult, "The combined annotations files should match");
	}

	@Test
	void testCreateZip() throws Exception {

		ClassLoader classLoader = getClass().getClassLoader();
		URL resourcePath = classLoader.getResource("zip");
		Path zipResourcePath = Paths.get(resourcePath.getFile());
		File file = zipResourcePath.toFile();
		String zipFileNameString = "reqstool-test-zip";

		RequirementsToolMojo mojo = new RequirementsToolMojo();

		Field outputDirectory = RequirementsToolMojo.class.getDeclaredField("outputDirectory");
		Field datasetPath = RequirementsToolMojo.class.getDeclaredField("datasetPath");
		Field zipFileName = RequirementsToolMojo.class.getDeclaredField("zipFilename");
		Field failsafeReportsDir = RequirementsToolMojo.class.getDeclaredField("failsafeReportsDir");
		Field surefireReportsDir = RequirementsToolMojo.class.getDeclaredField("surefireReportsDir");

		outputDirectory.setAccessible(true);
		datasetPath.setAccessible(true);
		zipFileName.setAccessible(true);
		failsafeReportsDir.setAccessible(true);
		surefireReportsDir.setAccessible(true);

		outputDirectory.set(mojo, file);
		datasetPath.set(mojo, file);
		zipFileName.set(mojo, zipFileNameString);
		failsafeReportsDir.set(mojo, file);
		surefireReportsDir.set(mojo, file);

		Method zipMethod = RequirementsToolMojo.class.getDeclaredMethod("createZipFile");
		zipMethod.setAccessible(true);

		zipMethod.invoke(mojo);

		Path zipfileTargetPath = Paths.get("target", "test-classes", "zip");
		Path expectedZipFilePath = zipfileTargetPath.resolve(zipFileNameString);

		assertTrue(Files.exists(expectedZipFilePath));

	}

}
