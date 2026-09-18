# CS3227 MP 2

Incident Desk is a local JavaFX desktop application for company incident
reporting. It targets Java 25 and is built with the checked-in Gradle Wrapper.

See [the developer guide](docs/DeveloperGuide.md) for setup, build, test, and
architecture details.

## Agent usage guide

This repository provides three project-specific agent skills:

- `code-quality-review`: Reviews non-trivial code changes for readability,
  naming, unsafe shortcuts, and comment quality.
- `git-commit`: Inspects changes, prepares an accurate commit message, and
  creates a commit when requested.
- `log-agent-conversation`: Saves the visible conversation as a Markdown
  snapshot when explicitly requested.
