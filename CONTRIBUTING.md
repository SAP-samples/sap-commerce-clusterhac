# Contributing to ClusterHAC

Thank you for your interest in contributing to ClusterHAC. This document explains how to report issues and submit changes.

## Code of Conduct

All members of the project community must abide by the [SAP Open Source Code of Conduct](https://github.com/SAP-samples/.github/blob/main/CODE_OF_CONDUCT.md). Only by respecting each other can we develop a productive, collaborative community. Instances of abusive, harassing, or otherwise unacceptable behavior may be reported by contacting a project maintainer or [ospo@sap.com](mailto:ospo@sap.com).

## Reporting Issues

If you find a bug or have a question about the content, [create an issue](https://github.com/SAP-samples/sap-commerce-clusterhac/issues) in this repository. Please include:

- A clear description of the problem or question
- Steps to reproduce (for bugs), with the SAP Commerce version and cluster topology where relevant
- Expected versus actual behavior

## Contributing Code

If you wish to contribute code, offer fixes, or make improvements, please send a pull request.

1. Fork the repository and create your branch from `main`.
2. Make your change, keeping it focused and well scoped.
3. Ensure the extension builds and existing tests pass.
4. Open a pull request and fill in the template: what changed, why, and how you verified it.

### Before you open a pull request

- CI runs a secret scan, a REUSE licensing check and a check for environment host names on every
  pull request, next to CodeQL. Merge only when all of them pass.
- The unit tests need an SAP Commerce installation, so CI cannot run them. Run both commands
  locally and paste the results in the pull request. The second one runs the HAC module tests.
  - `ant unittests -Dtestclasses.extensions=clusterhac`
  - `ant unittests -Dtestclasses.extensions=clusterhac -Dtestclasses.web=true`
- Never commit customer data, customer names, credentials or real CCv2 and Cloud Logging Service
  host names. Use placeholders such as `https://dashboards-<subscription>.<region>.cls.services.cloud.sap`.
- Optional: `pip install pre-commit && pre-commit install` runs the same checks before each commit.
- Each pull request needs one approval from someone other than the last person who pushed to it.
  `.github/CODEOWNERS` names who reviews.

### Developer Certificate of Origin (DCO)

Due to legal reasons, contributors will be asked to accept a DCO when they create their first pull request to this project. This happens in an automated fashion during the submission process. SAP uses [the standard DCO text of the Linux Foundation](https://developercertificate.org/).

## License

By contributing to this project, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
