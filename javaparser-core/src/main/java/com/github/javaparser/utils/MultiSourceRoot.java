/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2023 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */
package com.github.javaparser.utils;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.github.javaparser.utils.Utils.assertNotNull;

/**
 * A {@link SourceRoot} that delegates to multiple {@link SourceRoot SourceRoots}. The first root behaves exactly like a
 * normal {@code SourceRoot}; additional roots are selected based on the package name that is being parsed or written.
 * This allows the generators to work across several independent source trees (for example, when experimenting with
 * AST nodes that live outside {@code javaparser-core}).
 */
public class MultiSourceRoot extends SourceRoot {

    private final Map<String, SourceRoot> delegatedRoots;

    /**
     * Creates a {@code MultiSourceRoot} with the given primary root and additional package roots.
     *
     * @param primaryRoot the regular javaparser-core source root.
     * @param configuration the parser configuration to use for every root.
     * @param additionalRoots a mapping from a package prefix (for example {@code "org.mvel3"}) to another source tree
     *                        that should be consulted when that package (or one of its sub-packages) is parsed.
     */
    public MultiSourceRoot(Path primaryRoot, ParserConfiguration configuration, Map<String, Path> additionalRoots) {
        super(primaryRoot);
        this.delegatedRoots = new LinkedHashMap<>();
        if (additionalRoots != null) {
            additionalRoots.forEach((pkg, path) -> {
                String normalizedPrefix = normalizePrefix(pkg);
                SourceRoot delegate = configuration == null ? new SourceRoot(path) : new SourceRoot(path, configuration);
                delegatedRoots.put(normalizedPrefix, delegate);
            });
        }
        if (configuration != null) {
            setParserConfiguration(configuration);
        }
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String trimmed = prefix.trim();
        if (trimmed.endsWith(".")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Optional<SourceRoot> findDelegate(String startPackage) {
        String candidate = startPackage == null ? "" : startPackage;
        // Prefer the most specific (longest) prefix.
        return delegatedRoots.entrySet().stream().filter(entry -> packageMatches(candidate, entry.getKey())).sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length())).map(Map.Entry::getValue).findFirst();
    }

    private static boolean packageMatches(String candidate, String prefix) {
        if (prefix.isEmpty()) {
            return false;
        }
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        return candidate.equals(prefix) || candidate.startsWith(prefix + ".");
    }

    private List<SourceRoot> allRoots() {
        return new ArrayList<>(delegatedRoots.values());
    }

    @Override
    public ParseResult<CompilationUnit> tryToParse(String startPackage, String filename, ParserConfiguration configuration) throws IOException {
        Optional<SourceRoot> delegate = findDelegate(startPackage);
        if (delegate.isPresent()) {
            delegate.get().setParserConfiguration(configuration);
            return delegate.get().tryToParse(startPackage, filename, configuration);
        }
        return super.tryToParse(startPackage, filename, configuration);
    }

    @Override
    public ParseResult<CompilationUnit> tryToParse(String startPackage, String filename) throws IOException {
        Optional<SourceRoot> delegate = findDelegate(startPackage);
        if (delegate.isPresent()) {
            return delegate.get().tryToParse(startPackage, filename, getParserConfiguration());
        }
        return super.tryToParse(startPackage, filename);
    }

    @Override
    public CompilationUnit parse(String startPackage, String filename) {
        Optional<SourceRoot> delegate = findDelegate(startPackage);
        if (delegate.isPresent()) {
            return delegate.get().parse(startPackage, filename);
        }
        return super.parse(startPackage, filename);
    }

    @Override
    public SourceRoot add(String startPackage, String filename, CompilationUnit compilationUnit) {
        assertNotNull(compilationUnit);
        Optional<SourceRoot> delegate = findDelegate(startPackage);
        if (delegate.isPresent()) {
            delegate.get().add(startPackage, filename, compilationUnit);
            return this;
        }
        return super.add(startPackage, filename, compilationUnit);
    }

    @Override
    public SourceRoot add(CompilationUnit compilationUnit) {
        assertNotNull(compilationUnit);
        String pkg = compilationUnit.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        Optional<SourceRoot> delegate = findDelegate(pkg);
        if (delegate.isPresent()) {
            delegate.get().add(compilationUnit);
            return this;
        }
        return super.add(compilationUnit);
    }

    @Override
    public List<ParseResult<CompilationUnit>> tryToParse(String startPackage) throws IOException {
        if (startPackage == null || startPackage.isEmpty()) {
            return super.tryToParse("");
        }
        Optional<SourceRoot> delegate = findDelegate(startPackage);
        if (delegate.isPresent()) {
            return delegate.get().tryToParse(startPackage);
        }
        return super.tryToParse(startPackage);
    }

    @Override
    public List<ParseResult<CompilationUnit>> tryToParse() throws IOException {
        return super.tryToParse();
    }

    @Override
    public MultiSourceRoot setParserConfiguration(ParserConfiguration parserConfiguration) {
        super.setParserConfiguration(parserConfiguration);
        for (SourceRoot delegate : allRoots()) {
            delegate.setParserConfiguration(parserConfiguration);
        }
        return this;
    }

    @Override
    public MultiSourceRoot setPrinter(java.util.function.Function<CompilationUnit, String> printer) {
        super.setPrinter(printer);
        for (SourceRoot delegate : allRoots()) {
            delegate.setPrinter(printer);
        }
        return this;
    }

    @Override
    public SourceRoot saveAll(Path root, Charset encoding) {
        super.saveAll(root, encoding);
        for (SourceRoot delegate : allRoots()) {
            delegate.saveAll(delegate.getRoot(), encoding);
        }
        return this;
    }

    @Override
    public SourceRoot saveAll(Path root) {
        super.saveAll(root);
        for (SourceRoot delegate : allRoots()) {
            delegate.saveAll();
        }
        return this;
    }

    @Override
    public SourceRoot saveAll() {
        super.saveAll();
        for (SourceRoot delegate : allRoots()) {
            delegate.saveAll();
        }
        return this;
    }

    @Override
    public SourceRoot saveAll(Charset encoding) {
        super.saveAll(encoding);
        for (SourceRoot delegate : allRoots()) {
            delegate.saveAll(encoding);
        }
        return this;
    }

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        List<CompilationUnit> compilationUnits = new ArrayList<>(super.getCompilationUnits());
        for (SourceRoot delegate : allRoots()) {
            compilationUnits.addAll(delegate.getCompilationUnits());
        }
        return compilationUnits;
    }

    @Override
    public List<ParseResult<CompilationUnit>> getCache() {
        List<ParseResult<CompilationUnit>> cache = new ArrayList<>(super.getCache());
        for (SourceRoot delegate : allRoots()) {
            cache.addAll(delegate.getCache());
        }
        return cache;
    }

    /**
     * Exposes the additional roots in an immutable fashion (useful for diagnostics/tests).
     */
    public Map<String, SourceRoot> getDelegatedRoots() {
        return Collections.unmodifiableMap(delegatedRoots);
    }
}
