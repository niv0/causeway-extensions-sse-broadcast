# GitHub Actions Workflow for causeway-extensions-sse-broadcast

This module includes a GitHub Actions workflow that automatically builds, tests, and publishes the Maven package to GitHub Packages (private Maven registry).

## Workflow Features

The workflow (`build-and-publish.yml`) performs the following:

1. **Build**: Compiles the Maven project
2. **Test**: Runs unit tests
3. **Version Management**: Automatically increments patch version on push to `master`/`main`
4. **Publish**: Deploys artifacts to GitHub Packages Maven registry
5. **Release**: Creates GitHub releases with tags
6. **Caching**: Uses Maven dependency caching for faster builds

## Triggers

The workflow runs on:
- Push to `master` or `main` branch (with version bump and publish)
- Pull requests (build and test only)
- Manual trigger via workflow_dispatch

## Using the Package from GitHub Packages

### Prerequisites

You need a GitHub Personal Access Token (PAT) with `read:packages` scope.

### Configure Maven Settings

Add the following to your `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

### Add Repository to Your Project

Add this to your project's `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/OWNER/REPO</url>
    </repository>
</repositories>
```

Replace `OWNER/REPO` with the actual GitHub repository path (e.g., `vnisevic/tuepl-causeway`).

### Add Dependency

```xml
<dependency>
    <groupId>org.apache.causeway.extensions</groupId>
    <artifactId>causeway-extensions-sse-broadcast</artifactId>
    <version>3.5.0</version>
</dependency>
```

## Environment Variables

The workflow uses these GitHub secrets:
- `GITHUB_TOKEN` - Automatically provided by GitHub Actions

## Local Development

To build and test locally:

```bash
# Build
mvn clean install

# Run tests
mvn test

# Deploy to GitHub Packages (requires authentication)
mvn deploy
```

## Version Management

- Versions follow semantic versioning: `MAJOR.MINOR.PATCH`
- The workflow automatically increments the `PATCH` version on each push to master/main
- Manual version changes can be made by editing `pom.xml`
- Each version is tagged as `v{version}` (e.g., `v3.5.1`)

## Viewing Packages

Published packages can be viewed at:
```
https://github.com/OWNER/REPO/packages
```

Replace `OWNER/REPO` with your repository path.

