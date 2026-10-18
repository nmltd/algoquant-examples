# Demo migration

Source: `nmltd/algoquant` commit `fd0d412baf3d46eb7b4505b03ad246b253ba266c`, directory `demo/`.

The directory contents are merged into this repository's root, matching the technical
demos already migrated here. All 57 remaining Java source files retain their packages.
The data files, config and IB assets keep their relative paths and original bytes.
`demo/pom.xml` is replaced by the existing standalone public AlgoQuant dependency;
there is no dependency on private `algoquant-core` or its build parent.

The generated `demo-migration.tsv` records every copied file and its original Git blob
SHA. The migration verifies those hashes before committing. No original private Git
history, core sources or credentials are copied.

Validation compiles/packages every example against the published obfuscated combined
JAR with tests skipped. It does not launch desktop apps, database queries or live IB
sessions. Some imported research programs retain environment-specific paths and service
requirements; those are outside this file move. Existing synthetic examples and web apps
retain their paths and source links.
