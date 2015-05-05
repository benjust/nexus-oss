/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.testsuite.maven.metadata;

import java.io.File;
import java.util.Arrays;

import javax.inject.Inject;

import org.sonatype.nexus.common.io.DirSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.maven.MavenHostedFacet;
import org.sonatype.nexus.testsuite.NexusCoreITSupport;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

/**
 * Metadata rebuild IT.
 */
@ExamReactorStrategy(PerClass.class)
public class RebuildMetadataIT
    extends NexusCoreITSupport
{
  @Inject
  private RepositoryManager repositoryManager;

  @Configuration
  public static Option[] configureNexus() {
    return options(nexusDistribution("org.sonatype.nexus.assemblies", "nexus-base-template"),
        wrappedBundle(maven("org.apache.maven.shared", "maven-verifier").versionAsInProject()),
        wrappedBundle(maven("org.apache.maven.shared", "maven-shared-utils").versionAsInProject()));
  }

  @Test
  public void rebuildMetadataUpdate() throws Exception {
    File baseDir = resolveBaseFile("target/maven-rebuild-metadata/testproject").getAbsoluteFile();
    DirSupport.mkdir(baseDir.toPath());

    final String settingsXml = Files.toString(resolveTestFile("settings.xml"), Charsets.UTF_8).replace(
        "${nexus.port}", String.valueOf(nexusUrl.getPort()));
    File settings = new File(baseDir, "settings.xml").getAbsoluteFile();
    Files.write(settingsXml, settings, Charsets.UTF_8);

    DirSupport.copy(resolveTestFile("testproject").toPath(), baseDir.toPath());

    Verifier verifier = new Verifier(baseDir.getAbsolutePath());
    verifier.addCliOption("-s " + settings.getAbsolutePath());
    verifier.addCliOption(
        // TODO: verifier replaces // -> /
        "-DaltDeploymentRepository=local-nexus-admin::default::http:////localhost:" + nexusUrl.getPort() +
            "/repository/maven-snapshots");
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    verifier.verifyErrorFreeLog();

    final Repository mavenSnapshots = repositoryManager.get("maven-snapshots");
    final MavenHostedFacet mavenHostedFacet = mavenSnapshots.facet(MavenHostedFacet.class);
    // update=true does NOT work as there are no blobs -> java.lang.IllegalStateException: Blob not found: STORE@NODE:00000000000008c0
    mavenHostedFacet.rebuildMetadata(true, null, null, null);

    // TODO: verification
  }

  @Test
  public void rebuildMetadataRepair() throws Exception {
    File baseDir = resolveBaseFile("target/maven-rebuild-metadata/testproject").getAbsoluteFile();
    DirSupport.mkdir(baseDir.toPath());

    final String settingsXml = Files.toString(resolveTestFile("settings.xml"), Charsets.UTF_8).replace(
        "${nexus.port}", String.valueOf(nexusUrl.getPort()));
    File settings = new File(baseDir, "settings.xml").getAbsoluteFile();
    Files.write(settingsXml, settings, Charsets.UTF_8);

    DirSupport.copy(resolveTestFile("testproject").toPath(), baseDir.toPath());

    Verifier verifier = new Verifier(baseDir.getAbsolutePath());
    verifier.addCliOption("-s " + settings.getAbsolutePath());
    verifier.addCliOption(
        // TODO: verifier replaces // -> /
        "-DaltDeploymentRepository=local-nexus-admin::default::http:////localhost:" + nexusUrl.getPort() +
            "/repository/maven-snapshots");
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    verifier.verifyErrorFreeLog();

    final Repository mavenSnapshots = repositoryManager.get("maven-snapshots");
    final MavenHostedFacet mavenHostedFacet = mavenSnapshots.facet(MavenHostedFacet.class);
    // update=true does NOT work as there are no blobs -> java.lang.IllegalStateException: Blob not found: STORE@NODE:00000000000008c0
    mavenHostedFacet.rebuildMetadata(false, null, null, null);

    // TODO: verification
  }
}
