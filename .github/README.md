# GitHub Integration for causeway-extensions-sse-broadcast

This directory contains GitHub Actions workflows and documentation for automated building, testing, and publishing of the Maven package.

## 📁 Directory Structure

```
.github/
├── workflows/
│   └── build-and-publish.yml    # Main CI/CD workflow
├── GITHUB_PACKAGES.md            # Guide for consuming packages
├── SETUP_SUMMARY.md              # Complete setup documentation
└── WORKFLOW_SETUP.md             # Workflow reference and troubleshooting
```

## 🚀 Quick Start

### For Maintainers

1. **Push changes to master/main**
   ```bash
   git add .
   git commit -m "feat: your changes"
   git push origin master
   ```

2. **Monitor workflow**
   - Go to GitHub → Actions tab
   - Watch the build progress
   - Check for any errors

3. **Verify publication**
   - Go to GitHub → Packages tab
   - Confirm new version is published

### For Consumers

1. **Configure Maven settings** (`~/.m2/settings.xml`)
   ```xml
   <servers>
       <server>
           <id>github</id>
           <username>YOUR_USERNAME</username>
           <password>YOUR_GITHUB_PAT</password>
       </server>
   </servers>
   ```

2. **Add repository to pom.xml**
   ```xml
   <repositories>
       <repository>
           <id>github</id>
           <url>https://maven.pkg.github.com/OWNER/REPO</url>
       </repository>
   </repositories>
   ```

3. **Add dependency**
   ```xml
   <dependency>
       <groupId>org.apache.causeway.extensions</groupId>
       <artifactId>causeway-extensions-sse-broadcast</artifactId>
       <version>3.5.0</version>
   </dependency>
   ```

## 📋 Workflow Overview

### Triggers
- ✅ Push to `master` or `main`
- ✅ Pull requests
- ✅ Manual dispatch (workflow_dispatch)

### Jobs

#### On Push to Master/Main
1. Build and test
2. Increment version (PATCH)
3. Deploy to GitHub Packages
4. Create Git tag
5. Create GitHub Release

#### On Pull Request
1. Build and test only
2. No deployment

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **SETUP_SUMMARY.md** | Complete overview of the setup, features, and usage |
| **WORKFLOW_SETUP.md** | Technical reference for workflow configuration |
| **GITHUB_PACKAGES.md** | Guide for consuming packages from GitHub Packages |

## 🔧 Maintenance

### Manual Version Update
```bash
# Update to specific version
mvn versions:set -DnewVersion=4.0.0
git add pom.xml
git commit -m "chore: bump to version 4.0.0"
git push
```

### Skip CI
Add `[skip ci]` to commit message:
```bash
git commit -m "docs: update README [skip ci]"
```

### View Workflow Logs
1. Go to GitHub repository
2. Click "Actions" tab
3. Select workflow run
4. View logs for each step

## 🔐 Security

- Uses `GITHUB_TOKEN` (automatically provided)
- No manual secrets configuration needed
- Requires permissions:
  - `contents: write`
  - `packages: write`

## ⚙️ Configuration

### Workflow File
Location: `workflows/build-and-publish.yml`

Key configurations:
- **JDK Version**: 17 (Temurin)
- **Maven Cache**: Enabled
- **Test Execution**: On all runs
- **Deployment**: Only on master/main push

### POM Configuration
Added to `pom.xml`:
- Distribution management for GitHub Packages
- Version management plugins
- Build helper plugins

## 🐛 Troubleshooting

### Build Fails
- Check Maven dependencies
- Verify JDK 17 compatibility
- Review workflow logs

### Authentication Issues
- Verify GitHub token has correct permissions
- Check Maven settings.xml configuration
- Ensure repository URL is correct

### Version Conflicts
- Check if version already exists in registry
- Manually increment version if needed
- Review Git tags

## 📊 Status

Current Status: ✅ **Ready for Production**

- Workflow validated
- POM.xml validated (`mvn validate` successful)
- Documentation complete
- No additional secrets required

## 🔗 Useful Links

- [GitHub Actions Documentation](https://docs.github.com/actions)
- [GitHub Packages Documentation](https://docs.github.com/packages)
- [Maven Deploy Plugin](https://maven.apache.org/plugins/maven-deploy-plugin/)
- [Semantic Versioning](https://semver.org/)

## 📝 Notes

- Automatic versioning increments PATCH only
- For MAJOR/MINOR updates, update manually
- Workflow respects branch protection rules
- Each release includes built JAR artifacts
- Workflow logs are retained for 90 days (GitHub default)

---

**Last Updated**: 2026-01-13  
**Module**: causeway-extensions-sse-broadcast  
**Version**: 3.5.0  
**Status**: ✅ Production Ready

