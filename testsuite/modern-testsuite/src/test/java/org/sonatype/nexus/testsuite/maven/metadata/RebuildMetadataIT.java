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
import java.io.IOException;
import java.util.Arrays;

import javax.inject.Inject;

import org.sonatype.nexus.common.io.DirSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.maven.MavenFacet;
import org.sonatype.nexus.repository.maven.MavenHostedFacet;
import org.sonatype.nexus.repository.maven.MavenPath;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.testsuite.NexusCoreITSupport;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.maven.it.Verifier;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
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

  private boolean deployed = false;

  private Repository mavenSnapshots;

  private MavenFacet mavenFacet;

  private File mavenBaseDir;

  private File mavenSettings;


  @Configuration
  public static Option[] configureNexus() {
    return options(nexusDistribution("org.sonatype.nexus.assemblies", "nexus-base-template"),
        wrappedBundle(maven("org.apache.maven.shared", "maven-verifier").versionAsInProject()),
        wrappedBundle(maven("org.apache.maven.shared", "maven-shared-utils").versionAsInProject()));
  }

  @Before
  public void prepare() throws Exception {
    mavenBaseDir = resolveBaseFile("target/maven-rebuild-metadata/testproject").getAbsoluteFile();
    DirSupport.mkdir(mavenBaseDir.toPath());

    final String settingsXml = Files.toString(resolveTestFile("settings.xml"), Charsets.UTF_8).replace(
        "${nexus.port}", String.valueOf(nexusUrl.getPort()));
    mavenSettings = new File(mavenBaseDir, "settings.xml").getAbsoluteFile();
    Files.write(settingsXml, mavenSettings, Charsets.UTF_8);

    DirSupport.copy(resolveTestFile("testproject").toPath(), mavenBaseDir.toPath());

    mavenSnapshots = repositoryManager.get("maven-snapshots");
    mavenFacet = mavenSnapshots.facet(MavenFacet.class);

    // HACK: deploy once two times
    if (!deployed) {
      mvnDeploy();
      mvnDeploy();
      deployed = true;
    }
  }

  private void mvnDeploy() throws Exception {
    Verifier verifier = new Verifier(mavenBaseDir.getAbsolutePath());
    verifier.addCliOption("-s " + mavenSettings.getAbsolutePath());
    verifier.addCliOption(
        // TODO: verifier replaces // -> /
        "-DaltDeploymentRepository=local-nexus-admin::default::http:////localhost:" + nexusUrl.getPort() +
            "/repository/maven-snapshots");
    verifier.executeGoals(Arrays.asList("clean", "deploy"));
    verifier.verifyErrorFreeLog();
  }

  private Content content(String path) throws IOException {
    final MavenPath mavenPath = mavenFacet.getMavenPathParser().parsePath(path);
    return mavenFacet.get(mavenPath);
  }

  @Test
  public void rebuildMetadataWholeRepository() throws Exception {
    final Repository mavenSnapshots = repositoryManager.get("maven-snapshots");
    final MavenHostedFacet mavenHostedFacet = mavenSnapshots.facet(MavenHostedFacet.class);
    mavenHostedFacet.rebuildMetadata(null, null, null);

    final Content gLevel = content("/org/sonatype/nexus/testsuite/maven-metadata.xml");
    assertThat(gLevel, notNullValue());
    final Content aLevel = content("/org/sonatype/nexus/testsuite/testproject/maven-metadata.xml");
    assertThat(aLevel, notNullValue());
    final Content vLevel = content("/org/sonatype/nexus/testsuite/testproject/1.0-SNAPSHOT/maven-metadata.xml");
    assertThat(vLevel, notNullValue());
  }

  @Test
  public void rebuildMetadataGroup() throws Exception {
    final Repository mavenSnapshots = repositoryManager.get("maven-snapshots");
    final MavenHostedFacet mavenHostedFacet = mavenSnapshots.facet(MavenHostedFacet.class);
    mavenHostedFacet.rebuildMetadata("org.sonatype.nexus.testsuite", null, null); // testproject groupId!

    final Content gLevel = content("/org/sonatype/nexus/testsuite/maven-metadata.xml");
    assertThat(gLevel, notNullValue());
    final Content aLevel = content("/org/sonatype/nexus/testsuite/testproject/maven-metadata.xml");
    assertThat(aLevel, notNullValue());
    final Content vLevel = content("/org/sonatype/nexus/testsuite/testproject/1.0-SNAPSHOT/maven-metadata.xml");
    assertThat(vLevel, notNullValue());
  }
}
