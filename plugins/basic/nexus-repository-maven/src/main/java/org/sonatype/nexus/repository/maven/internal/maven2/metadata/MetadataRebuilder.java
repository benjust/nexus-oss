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
package org.sonatype.nexus.repository.maven.internal.maven2.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.maven.MavenFacet;
import org.sonatype.nexus.repository.maven.MavenPath;
import org.sonatype.nexus.repository.maven.MavenPath.HashType;
import org.sonatype.nexus.repository.maven.MavenPathParser;
import org.sonatype.nexus.repository.maven.internal.DigestExtractor;
import org.sonatype.nexus.repository.maven.internal.maven2.Maven2Format;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.BucketEntityAdapter;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.payloads.StringPayload;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.orientechnologies.orient.core.command.OCommandResultListener;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLAsynchQuery;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Maven 2 repository metadata re-builder.
 *
 * @since 3.0
 */
@Singleton
@Named
@ThreadSafe
public class MetadataRebuilder
    extends ComponentSupport
{
  private final BucketEntityAdapter bucketEntityAdapter;

  @Inject
  public MetadataRebuilder(final BucketEntityAdapter bucketEntityAdapter)
  {
    this.bucketEntityAdapter = checkNotNull(bucketEntityAdapter);
  }

  /**
   * Rebuilds/updates Maven metadata.
   *
   * @param repository  The repository who's metadata needs rebuild (Maven2 format, Hosted type only).
   * @param update      if {@code true}, updates existing metadata, otherwise overwrites them with newly generated
   *                    ones.
   * @param groupId     scope the work to given groupId.
   * @param artifactId  scope the work to given artifactId (groupId must be given).
   * @param baseVersion scope the work to given baseVersion (groupId and artifactId must ge given).
   */
  public void rebuild(final Repository repository,
                      final boolean update,
                      @Nullable final String groupId,
                      @Nullable final String artifactId,
                      @Nullable final String baseVersion)
  {
    checkNotNull(repository);
    final StringBuilder sql = new StringBuilder(
        "SELECT " +
            "group as groupId, " +
            "name as artifactId, " +
            "set(attributes.maven2.baseVersion) as baseVersions " +
            "FROM component WHERE bucket=:bucket"
    );
    final Map<String, Object> sqlParams = Maps.newHashMap();
    if (!Strings.isNullOrEmpty(groupId)) {
      sql.append(" and group=:groupId");
      sqlParams.put("groupId", groupId);
      if (!Strings.isNullOrEmpty(artifactId)) {
        sql.append(" and name=:artifactId");
        sqlParams.put("artifactId", artifactId);
        if (!Strings.isNullOrEmpty(baseVersion)) {
          sql.append(" and attributes.maven2.baseVersion=:baseVersion");
          sqlParams.put("baseVersion", baseVersion);
        }
      }
    }
    sql.append(" GROUP BY group, name");
    final Worker worker = new Worker(repository, update, sql.toString(), sqlParams);
    try (StorageTx tx = repository.facet(StorageFacet.class).openTx()) {
      final ORID bucketOrid = bucketEntityAdapter.decode(tx.getBucket().getEntityMetadata().getId());
      sqlParams.put("bucket", bucketOrid);
      worker.rebuildMetadata(tx);
    }
  }

  /**
   * Inner class (non static) that encapsulates the work, as metadata builder is stateful.
   */
  private class Worker
  {
    private final boolean update;

    private final String sql;

    private final Map<String, Object> sqlParams;

    private final Repository repository;

    private final StorageFacet storageFacet;

    private final MavenFacet mavenFacet;

    private final MavenPathParser mavenPathParser;

    private final MetadataBuilder metadataBuilder;

    private final MetadataUpdater metadataUpdater;

    private final DocumentBuilderFactory documentBuilderFactory;

    public Worker(final Repository repository,
                  final boolean update,
                  final String sql,
                  final Map<String, Object> sqlParams)
    {
      this.update = update;
      this.sql = sql;
      this.sqlParams = sqlParams;
      this.repository = repository;
      this.storageFacet = repository.facet(StorageFacet.class);
      this.mavenFacet = repository.facet(MavenFacet.class);
      this.mavenPathParser = mavenFacet.getMavenPathParser();
      this.metadataBuilder = new MetadataBuilder();
      this.metadataUpdater = new MetadataUpdater(mavenFacet);
      this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
    }

    /**
     * Method rebuilding metadata that performs the group level processing. It uses memory conservative "async" SQL
     * approach, and calls {@link #rebuildMetadataInner(String, String, Set)} method as results are arriving.
     */
    public void rebuildMetadata(final StorageTx tx)
    {
      final ODatabaseDocumentTx readDatabase = tx.getDb();
      readDatabase.command(
          new OSQLAsynchQuery<ODocument>(
              sql,
              new OCommandResultListener()
              {
                String currentGroupId = null;

                @Override
                public boolean result(Object iRecord) {
                  try {
                    final ODocument doc = (ODocument) iRecord;
                    final String groupId = doc.field("groupId", OType.STRING);
                    final String artifactId = doc.field("artifactId", OType.STRING);
                    final Set<String> baseVersions = doc.field("baseVersions", OType.EMBEDDEDSET);

                    final boolean groupChange = !Objects.equals(currentGroupId, groupId);
                    if (groupChange) {
                      if (currentGroupId != null) {
                        processMetadata(metadataMavenPath(currentGroupId, null, null), metadataBuilder.onExitGroupId());
                      }
                      currentGroupId = groupId;
                      metadataBuilder.onEnterGroupId(groupId);
                    }
                    rebuildMetadataInner(groupId, artifactId, baseVersions);
                    return true;
                  }
                  finally {
                    // hack to restore potential write TX/DB uses of inner method: reset thread-bound DB
                    ODatabaseRecordThreadLocal.INSTANCE.set(readDatabase);
                  }
                }

                @Override
                public void end() {
                  if (currentGroupId != null) {
                    processMetadata(metadataMavenPath(currentGroupId, null, null), metadataBuilder.onExitGroupId());
                  }
                }
              }
          )
      ).execute(sqlParams);
    }

    /**
     * Method rebuilding metadata that performs artifact and baseVersion processing. While it is called from {@link
     * #rebuildMetadata(StorageTx)} method, it will use a separate TX/DB to perform writes, it does NOT accept the TX
     * from caller.
     */
    private void rebuildMetadataInner(final String groupId,
                                      final String artifactId,
                                      final Set<String> baseVersions)
    {
      metadataBuilder.onEnterArtifactId(artifactId);
      for (String baseVersion : baseVersions) {
        metadataBuilder.onEnterBaseVersion(baseVersion);
        try (StorageTx tx = storageFacet.openTx()) {
          final Iterable<Component> components = tx.findComponents(
              "group = :groupId and name = :artifactId and attributes.maven2.baseVersion = :baseVersion",
              ImmutableMap.<String, Object>of(
                  "groupId", groupId,
                  "artifactId", artifactId,
                  "baseVersion", baseVersion
              ),
              ImmutableList.of(repository),
              null // order by
          );
          for (Component component : components) {
            final Iterable<Asset> assets = tx.browseAssets(component);
            for (Asset asset : assets) {
              final MavenPath mavenPath = mavenPathParser.parsePath(
                  asset.formatAttributes().require(StorageFacet.P_PATH, String.class)
              );
              if (mavenPath.isSubordinate()) {
                continue;
              }
              metadataBuilder.addArtifactVersion(mavenPath);
              mayUpdateChecksum(asset, mavenPath, HashType.SHA1);
              mayUpdateChecksum(asset, mavenPath, HashType.MD5);
              if (mavenPath.isPom()) {
                final Document pom = getModel(mavenPath);
                if (pom != null) {
                  final String packaging = getChildValue(pom, "packaging", "jar");
                  if ("maven-plugin".equals(packaging)) {
                    metadataBuilder.addPlugin(getPluginPrefix(mavenPath.locateMain("jar")), artifactId,
                        getChildValue(pom, "name", null));
                  }
                }
              }
            }
          }
        }
        processMetadata(metadataMavenPath(groupId, artifactId, baseVersion), metadataBuilder.onExitBaseVersion());
      }
      processMetadata(metadataMavenPath(groupId, artifactId, null), metadataBuilder.onExitArtifactId());
    }

    /**
     * Verifies and may fix/create the broken/non-existent Maven hashes (.sha1/.md5 files).
     */
    private void mayUpdateChecksum(final Asset asset, final MavenPath mavenPath, final HashType hashType) {
      final AttributesMap checksums = asset.attributes().child(StorageFacet.P_CHECKSUM);
      checkState(checksums != null, "checksums");
      final String assetChecksum = (String) checksums.get(hashType.getHashAlgorithm().name());
      checkState(!Strings.isNullOrEmpty(assetChecksum), "assetChecksum");
      final MavenPath checksumPath = mavenPath.hash(hashType);
      try {
        final Content content = mavenFacet.get(checksumPath);
        if (content != null) {
          try (InputStream is = content.openInputStream()) {
            final String mavenChecksum = DigestExtractor.extract(is);
            if (Objects.equals(assetChecksum, mavenChecksum)) {
              return; // all is OK: exists and matches
            }
          }
        }
      }
      catch (IOException e) {
        // TODO: ignore that underlying storage puke on read?
        log.warn("Error reading {}", checksumPath, e);
      }
      // we need to generate/write it
      try {
        final StringPayload mavenChecksum = new StringPayload(assetChecksum, Maven2Format.CHECKSUM_CONTENT_TYPE);
        mavenFacet.put(checksumPath, mavenChecksum);
      }
      catch (IOException e) {
        log.warn("Error writing {}", checksumPath, e);
        throw Throwables.propagate(e);
      }
    }

    /**
     * Assembles {@link MavenPath} for repository metadata out of groupId, artifactId and baseVersion.
     */
    private MavenPath metadataMavenPath(final String groupId,
                                        @Nullable final String artifactId,
                                        @Nullable final String baseVersion)
    {
      final StringBuilder sb = new StringBuilder("/");
      sb.append(groupId.replace('.', '/'));
      if (artifactId != null) {
        sb.append("/").append(artifactId);
        if (baseVersion != null) {
          sb.append("/").append(baseVersion);
        }
      }
      sb.append("/").append(Maven2Format.METADATA_FILENAME);
      return mavenPathParser.parsePath(sb.toString());
    }

    /**
     * Reads and parses Maven POM.
     */
    @Nullable
    private Document getModel(final MavenPath mavenPath) {
      // sanity checks: is artifact and extension is "pom", only possibility for maven POM currently
      checkArgument(mavenPath.isPom(), "Not a pom path: %s", mavenPath);
      try {
        final Content pomContent = mavenFacet.get(mavenPath);
        if (pomContent != null) {
          try (InputStream is = pomContent.openInputStream()) {
            final DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
            return db.parse(is);
          }
        }
      }
      catch (SAXException e) {
        log.debug("Could not parse POM: {}", mavenPath, e);
      }
      catch (Exception e) {
        throw Throwables.propagate(e);
      }
      return null;
    }

    /**
     * Returns the plugin prefix of a Maven plugin, by cranking up the plugin JAR, and reading the Maven Plugin
     * Descriptor. If fails, falls back to mangle artifactId (ie. extract XXX from XXX-maven-plugin or
     * maven-XXX-plugin).
     */
    private String getPluginPrefix(final MavenPath mavenPath) {
      // sanity checks: is artifact and extension is "jar", only possibility for maven plugins currently
      checkArgument(mavenPath.getCoordinates() != null);
      checkArgument(Objects.equals(mavenPath.getCoordinates().getExtension(), "jar"));
      String prefix = null;
      try {
        final Content jarFile = mavenFacet.get(mavenPath);
        if (jarFile != null) {
          try (ZipInputStream zip = new ZipInputStream(jarFile.openInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
              if (!entry.isDirectory() && entry.getName().equals("META-INF/maven/plugin.xml")) {
                final DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
                final Document document = db.parse(zip);
                prefix = getChildValue(document, "goalPrefix", null);
                zip.closeEntry();
                break;
              }
              zip.closeEntry();
            }
          }
        }
      }
      catch (Exception e) {
        log.debug("Unable to read plugin.xml of {}", mavenPath, e);
      }
      if (prefix != null) {
        return prefix;
      }
      if ("maven-plugin-plugin".equals(mavenPath.getCoordinates().getArtifactId())) {
        return "plugin";
      }
      else {
        return mavenPath.getCoordinates().getArtifactId().replaceAll("-?maven-?", "").replaceAll("-?plugin-?", "");
      }
    }

    /**
     * Helper method to get node's immediate child or default.
     */
    private String getChildValue(final Document doc, final String childName, final String defaultValue) {
      NodeList nl = doc.getElementsByTagName(childName);
      if (nl.getLength() == 0) {
        return defaultValue;
      }
      return nl.item(0).getNodeValue();
    }

    /**
     * Processes metadata, depending on {@link #update} value and input value of metadata parameter. If input is
     * non-null, will update or replace depending on value of {@link #update}. If update is null, will delete if {@link
     * #update} is {@code false}.
     */
    private void processMetadata(final MavenPath metadataPath, final Maven2Metadata metadata) {
      if (metadata != null) {
        if (update) {
          metadataUpdater.update(metadataPath, metadata);
        }
        else {
          metadataUpdater.replace(metadataPath, metadata);
        }
      }
      else if (!update) {
        metadataUpdater.delete(metadataPath);
      }
    }
  }
}
