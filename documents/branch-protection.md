# Main branch protection policy

Matches the owner's portfolio and Secure-Crypt branch rules, using CountCheck's own GitHub Actions job.

| Rule                            | Configuration                                |
| ------------------------------- | -------------------------------------------- |
| Pull request required           | Yes                                          |
| Required check                  | `verify`, from GitHub Actions (app ID 15368) |
| Up-to-date branch required      | Yes                                          |
| Dismiss stale approvals         | Yes                                          |
| Mandatory independent approvals | 0, matching the solo-maintainer policy       |
| Resolved conversations          | Required                                     |
| Apply to administrators         | Yes                                          |
| Force pushes                    | Disabled                                     |
| Branch deletion                 | Disabled                                     |
| Signed commits / linear history | Not required, matching existing repositories |

The CI workflow runs on every push and pull request, including documentation-only changes. Path filters were removed to avoid a permanently pending required check. Use pull requests for future changes to main; do not disable protection to push directly.
