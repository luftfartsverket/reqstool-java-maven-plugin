# reqstool-java-maven-plugin

## Description

This Maven plugin generates a zip artifact containing combined annotations and various reports for the LFV Reqstool.

## Configuration

Configure this <configuration> block into the <build> section of  pom.xml file to customize how your plugin behaves during the Maven build process.

### requirementsAnnotationsFile

The `requirementsAnnotationsFile` parameter specifies the path to the annotations file.

```
<configuration>
<requirementsAnnotationsFile>
${project.build.directory}/generated-sources/annotations/resources/annotations.yml
</requirementsAnnotationsFile>
</configuration>

```
## Usage

To use the LFV Reqstool Java Maven Plugin, add the following configuration to your Maven project's `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>se.lfv.reqstool.plugins.maven</groupId>
            <artifactId>reqstool-maven-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>attach-reqstool-zip</goal>
                    </goals>
                </execution>
            </executions>
            <dependencies>
                <dependency>
                    <groupId>se.lfv.requirements.annotations</groupId>
                    <artifactId>reqstool-annotations-java</artifactId>
                    <version>0.0.3-SNAPSHOT</version>
                </dependency>
            </dependencies>            
        </plugin>
    </plugins>
</build>
```
## License

This project is licensed under the MIT License.

