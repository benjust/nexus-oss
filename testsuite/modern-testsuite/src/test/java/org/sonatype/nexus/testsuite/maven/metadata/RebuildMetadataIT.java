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

import java.io.IOException;

import javax.inject.Inject;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.maven.MavenFacet;
import org.sonatype.nexus.repository.maven.MavenHostedFacet;
import org.sonatype.nexus.repository.maven.MavenPath;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.testsuite.maven.MavenITSupport;

import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Metadata rebuild IT.
 */
@ExamReactorStrategy(PerClass.class)
public class RebuildMetadataIT
    extends MavenITSupport
{
  @Inject
  private RepositoryManager repositoryManager;

  private boolean deployed = false;

  private Repository mavenSnapshots;

  private MavenFacet mavenFacet;

  private MavenHostedFacet mavenHostedFacet;

  @Before
  public void prepare() throws Exception {
    mavenSnapshots = repositoryManager.get("maven-snapshots");
    mavenFacet = mavenSnapshots.facet(MavenFacet.class);
    mavenHostedFacet = mavenSnapshots.facet(MavenHostedFacet.class);

    // HACK: deploy once two times
    if (!deployed) {
      deployed = true;
      mvnDeploy("testproject", mavenSnapshots.getName());
      mvnDeploy("testplugin", mavenSnapshots.getName());
    }
  }

  private Content content(String path) throws IOException {
    final MavenPath mavenPath = mavenFacet.getMavenPathParser().parsePath(path);
    return mavenFacet.get(mavenPath);
  }

  @Test
  public void rebuildMetadataWholeRepository() throws Exception {
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
    mavenHostedFacet.rebuildMetadata("org.sonatype.nexus.testsuite", null, null); // testproject groupId!

    final Content gLevel = content("/org/sonatype/nexus/testsuite/maven-metadata.xml");
    assertThat(gLevel, notNullValue());
    final Content aLevel = content("/org/sonatype/nexus/testsuite/testproject/maven-metadata.xml");
    assertThat(aLevel, notNullValue());
    final Content vLevel = content("/org/sonatype/nexus/testsuite/testproject/1.0-SNAPSHOT/maven-metadata.xml");
    assertThat(vLevel, notNullValue());
  }
}
