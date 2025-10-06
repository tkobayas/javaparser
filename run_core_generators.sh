#!/usr/bin/env bash

# Runs all the code generators.
# If the node structure was changed, run_metamodel_generator.sh first!

EXTRA_AST_ROOTS_ARGS=()
if [ -n "$JAVAPARSER_EXTRA_AST_ROOTS" ]; then
    EXTRA_AST_ROOTS_ARGS=("-Djavaparser.extraAstRoots=${JAVAPARSER_EXTRA_AST_ROOTS}")
fi

# Remember current directory
pushd javaparser-core-generators

# Generate code
../mvnw --errors --show-version -B clean package -P run-generators -DskipTests "${EXTRA_AST_ROOTS_ARGS[@]}"

# Go back to previous directory
popd

# Fresh code has been generated in core, so rebuild the whole thing again.
./mvnw --errors --show-version -B clean install -DskipTests "${EXTRA_AST_ROOTS_ARGS[@]}"
if [ "$?" -ne 0 ]; then
    exit 1
fi
