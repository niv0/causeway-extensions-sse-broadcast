# GitHub Actions Workflow Setup - Quick Reference

## Files Created

1. **`.github/workflows/build-and-publish.yml`** - Main GitHub Actions workflow
2. **`.github/GITHUB_PACKAGES.md`** - Documentation for using GitHub Packages

## POM.xml Changes

Added the following sections to `pom.xml`:

### Distribution Management
```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/${env.GITHUB_REPOSITORY}</url>
    </repository>
</distributionManagement>
```

### Version Management Plugins
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>versions-maven-plugin</artifactId>
    <version>2.17.1</version>
</plugin>
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <version>3.6.0</version>
</plugin>
```

## Workflow Behavior

### On Push to Master/Main
1. Checkout code
2. Set up JDK 17
3. Cache Maven dependencies
4. Bump patch version automatically
5. Build with Maven
6. Run tests
7. Deploy to GitHub Packages
8. Commit updated pom.xml
9. Create Git tag (v{version})
10. Create GitHub Release with artifacts

### On Pull Request
1. Build and test only
2. No version bump
3. No publish
4. No release

### Manual Trigger
- Can be triggered manually via GitHub Actions UI
- Behaves like a push to current branch

## Required Permissions

The workflow needs these GitHub permissions (already configured):
- `contents: write` - For committing version changes and creating releases
- `packages: write` - For publishing to GitHub Packages

## No Additional Secrets Required

The workflow uses `GITHUB_TOKEN` which is automatically provided by GitHub Actions.

## Testing the Workflow

1. Make a change to any Java file or pom.xml
2. Commit and push to master/main
3. Go to GitHub Actions tab to view workflow progress
4. Check GitHub Packages for published artifact

## Alternative: Using Nexus or Artifactory

To deploy to a different Maven registry (Nexus, Artifactory, etc.):

1. Update the `distributionManagement` URL in `pom.xml`
2. Add credentials as GitHub Secrets (NEXUS_USERNAME, NEXUS_PASSWORD)
3. Update the workflow to use these secrets:

```yaml
- name: Deploy to Nexus
  env:
    NEXUS_USERNAME: ${{ secrets.NEXUS_USERNAME }}
    NEXUS_PASSWORD: ${{ secrets.NEXUS_PASSWORD }}
  run: |
    mvn deploy \
      --batch-mode \
      -DskipTests \
      -s settings.xml
```

4. Create a `settings.xml` with server credentials

## Troubleshooting

### Build Fails
- Check Java version compatibility (JDK 17 required)
- Review Maven dependencies
- Check workflow logs in GitHub Actions

### Authentication Fails
- Ensure `GITHUB_TOKEN` has correct permissions
- For external registries, verify secrets are set

### Version Conflicts
- Check if version was already published
- Manually adjust version in pom.xml if needed

## Best Practices

1. **Semantic Versioning**: Follow MAJOR.MINOR.PATCH convention
2. **Branch Protection**: Enable branch protection on master/main
3. **Status Checks**: Require workflow to pass before merging PRs
4. **Release Notes**: Update workflow to include meaningful release notes
5. **Dependency Updates**: Keep Maven plugin versions up to date

## Next Steps

1. Push the changes to GitHub
2. Verify workflow runs successfully
3. Check GitHub Packages for published artifact
4. Test consuming the package in another project
5. Document usage in project README

## Contact

For issues or questions, refer to:
- GitHub Actions documentation: https://docs.github.com/actions
- GitHub Packages documentation: https://docs.github.com/packages
- Maven documentation: https://maven.apache.org/

