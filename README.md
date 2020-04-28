# cxod-spring-boot-java-sdk
Java Spring Boot SDK for CxOD

## Artifacts
Maven artifacts are stored on Sonatype nexus repository manager (synced to maven central)
https://oss.sonatype.org/service/local/repositories/releases/content/com/github/checkmarx-ts/cx-spring-boot-sdk/x.x.x/cxod-spring-boot-sdk-x.x.x.jar

_Note: Check maven version in current pom.xml_

### Build
```mvnw clean build```

_Note: add -DskipTests -Dgpg.skip flags to skip integration testing and gpg code signing (required for Sonatype)_

### Usage
#### Gradle
Include the following dependency in your maven project
```
compile('com.github.checkmarx-ts:cxod-spring-boot-sdk:X.X.X')
```
#### Maven
Include the following dependency in your maven project
```
<dependency>
    <groupId>com.github.checkmarx-ts</groupId>
    <artifactId>cxod-spring-boot-sdk</artifactId>
    <version>X.X.X</version>
</dependency>
```
#### Main Spring Boot Application
In the main spring boot application entry endpoint the following package scan must be added:
_com.checkmarx.sdk_
```java
@SpringBootApplication(scanBasePackages={"com.checkmarx.sdk","xxxx.xxxx.xxxx", ...})
```

#### CxClient Interface usage
Inject the dependency of CxClient / CxAuthClient / CxOsaClient (WIP).  This will inject the service bean to make calls to Checkmarx with
```java
@Component
@Command
public class SampleComponent {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SampleComponent.class);
    private final CxClient cxClient;
```

#### Configuration 
Checkmarx Specific properties are loaded from CxProperties class (config package).
```yaml
checkmarx:
  TBD:
```

These values can be injected at runtime by using environment variables and/or command line parameters.  See the following: https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html

_Note: The only required properties are username/password/base-url/team_
