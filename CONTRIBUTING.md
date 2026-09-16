# Contributing

Start with [`docs/PROCESS.md`](docs/PROCESS.md). Every change is one slice with declared unfrozen paths, numbered gates, a REVIEW audit, a local tag, and no push. Preserve the protocol direction: specs produce contracts, implementations consume them, and consumer extensions stay opaque.

Before opening a change:

1. Run the relevant focused tests and the required regression suite.
2. Check frozen paths and `git diff --check`.
3. Do not add personal data, secrets, generated local paths, or real chat captures.
4. Declare platform or implementation divergences in the REVIEW; never reconcile them silently.
5. Keep UNKNOWN and provenance explicit. Do not infer FACT from a receipt, sandbox, or model.

## Developer Certificate of Origin

By making a contribution to this project, I certify that:

```text
Developer Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I have the
    right to submit it under the open source license indicated in the file; or

(b) The contribution is based upon previous work that, to the best of my
    knowledge, is covered under an appropriate open source license and I have
    the right under that license to submit that work with modifications,
    whether created in whole or in part by me, under the same open source
    license (unless I am permitted to submit under a different license), as
    indicated in the file; or

(c) The contribution was provided directly to me by some other person who
    certified (a), (b) or (c) and I have not modified it.

(d) I understand and agree that this project and the contribution are public
    and that a record of the contribution (including all personal information
    I submit with it, including my sign-off) is maintained indefinitely and
    may be redistributed consistent with this project or the open source
    license(s) involved.

Signed-off-by: Full Name <email@example.com>
```

Contributors should sign commits with `git commit -s`. Do not use a placeholder identity in a real contribution.
