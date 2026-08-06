# ChunkedExplosions Documentation Index

Welcome to the ChunkedExplosions documentation! This index helps you navigate all available documentation resources.

## Table of Contents

### Getting Started
- [README](../README.md) - Project overview, quick start, and basic usage
- [Developer Guide](DEVELOPER_GUIDE.md) - Comprehensive guide for developers
- [API Reference](API_REFERENCE.md) - Complete API documentation

### Architecture
- [Explosion Architecture](architecture/explosion-architecture.md) - Core explosion system architecture (v3)
- [Implementation Plan](architecture/implementation-plan.md) - Original implementation planning document
- [Code Analysis](architecture/code-analysis.md) - Detailed codebase analysis
- [Lag Analysis Techniques](performance/lag-analysis-techniques.md) - Techniques for analyzing and reducing lag

### Planning
- [Dev Commands Plan](planning/dev-commands.md) - Development commands design and implementation plan
- [Test Plan](planning/test-plan.md) - Comprehensive testing strategy and procedures

### Testing
- [Manual Tests](testing/manual-tests/README.md) - Overview of manual testing procedures
- [Test Results](../test-results.md) - Summary of test results

### Configuration
- See [README.md](../README.md#configuration) for configuration options

### Code Documentation
All source code files contain extensive Javadoc comments. Key files to review:
- [`ChunkedExplosions.java`](../src/main/java/com/github/justoboy/chunkedexplosions/ChunkedExplosions.java) - Main mod entry point
- [`ModConfig.java`](../src/main/java/com/github/justoboy/chunkedexplosions/core/ModConfig.java) - Configuration management
- [`ExplosionProcessor.java`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionProcessor.java) - Queue management
- [`ExplosionState.java`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java) - Individual explosion processing
- [`BlockDestroyer.java`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/BlockDestroyer.java) - Block destruction handling

## Documentation Categories

### For New Developers
1. Start with the [README](../README.md) to understand what the mod does
2. Read the [Developer Guide](DEVELOPER_GUIDE.md) for in-depth technical information
3. Review the [API Reference](API_REFERENCE.md) for specific class/method details
4. Explore the [Architecture](architecture/) documents for system design understanding

### For Contributors
1. Review [Architecture](architecture/) documents to understand the system
2. Check [Planning](planning/) documents for development roadmap
3. Follow [Manual Tests](testing/manual-tests/) to verify your changes
4. Read [Lag Analysis Techniques](performance/lag-analysis-techniques.md) for performance considerations

### For Testers
1. Start with [Manual Tests](testing/manual-tests/README.md)
2. Review [Test Plan](planning/test-plan.md) for testing methodology
3. Check [Test Results](../test-results.md) for known issues and behaviors

## Documentation Maintenance

This documentation is maintained alongside the codebase. When making changes:
1. Update relevant documentation files
2. Add new files to this index
3. Ensure cross-references are updated
4. Run tests to verify documentation accuracy

---

*Last updated: 2026-08-06*
