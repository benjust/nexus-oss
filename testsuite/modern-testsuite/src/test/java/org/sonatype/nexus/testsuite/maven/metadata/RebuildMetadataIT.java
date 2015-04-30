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

import org.sonatype.nexus.orient.DatabaseManager;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.maven.MavenHostedFacet;
import org.sonatype.nexus.testsuite.maven.MavenITSupport;

import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
import org.junit.Before;
import org.junit.Test;

/**
 * Metadata rebuild IT.
 *
 * @since 3.0
 */
public class RebuildMetadataIT
    extends MavenITSupport
{
  @Inject
  private RepositoryManager repositoryManager;

  @Inject
  private DatabaseManager databaseManager;

  @Before
  public void importDatabase() throws IOException {
    try (ODatabaseDocumentTx db = databaseManager.instance("component").acquire()) {
      final ODatabaseImport imp = new ODatabaseImport(db, testData.resolveFile("component.gz").getAbsolutePath(),
          new OCommandOutputListener()
          {
            @Override
            public void onMessage(final String iText) {
              System.out.println(iText);
            }
          });
      imp.importDatabase();
      imp.close();
    }
  }

  @Test
  public void rebuildMetadata() throws Exception {
    final Repository mavenSnapshots = repositoryManager.get("maven-snapshots");
    final MavenHostedFacet mavenHostedFacet = mavenSnapshots.facet(MavenHostedFacet.class);
    mavenHostedFacet.rebuildMetadata(true, null, null, null);
  }
}
