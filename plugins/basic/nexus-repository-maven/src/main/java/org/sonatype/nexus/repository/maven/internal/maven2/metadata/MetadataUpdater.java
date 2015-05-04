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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.maven.MavenFacet;
import org.sonatype.nexus.repository.maven.MavenPath;
import org.sonatype.nexus.repository.maven.MavenPath.HashType;
import org.sonatype.nexus.repository.maven.internal.maven2.Maven2Format;
import org.sonatype.nexus.repository.maven.internal.maven2.Maven2MetadataMerger;
import org.sonatype.nexus.repository.maven.internal.maven2.Maven2MetadataMerger.MetadataEnvelope;
import org.sonatype.nexus.repository.maven.internal.maven2.metadata.MavenMetadata.Plugin;
import org.sonatype.nexus.repository.maven.internal.maven2.metadata.MavenMetadata.Snapshot;
import org.sonatype.nexus.repository.util.TypeTokens;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;
import org.sonatype.nexus.repository.view.payloads.StringPayload;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Maven 2 repository metadata updater.
 *
 * @since 3.0
 */
public class MetadataUpdater
    extends ComponentSupport
{
  private final MavenFacet mavenFacet;

  private final Maven2MetadataMerger metadataMerger;

  private final MetadataXpp3Reader metadataReader;

  private final MetadataXpp3Writer metadataWriter;

  private final DateFormat dotlessTimestampFormat;

  private final DateFormat dottedTimestampFormat;

  @Inject
  public MetadataUpdater(final MavenFacet mavenFacet) {
    this.mavenFacet = checkNotNull(mavenFacet);
    this.metadataMerger = new Maven2MetadataMerger();
    this.metadataReader = new MetadataXpp3Reader();
    this.metadataWriter = new MetadataXpp3Writer();
    this.dotlessTimestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    this.dotlessTimestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    this.dottedTimestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss");
    this.dottedTimestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  /**
   * Writes/updates metadata, merges existing one, if any.
   */
  public void update(final MavenPath mavenPath, final MavenMetadata metadata) {
    checkNotNull(mavenPath);
    checkNotNull(metadata);
    try {
      final Metadata oldMetadata = read(mavenPath);
      if (oldMetadata == null) {
        // old does not exists, just write it
        write(mavenPath, toMetadata(metadata));
      }
      else {
        // TODO: compare? unsure is it worth it, as compare would also eat CPU maybe even more that writing would
        // update old by merging them and write out
        final Metadata updated = metadataMerger.merge(
            ImmutableList.of(
                new MetadataEnvelope("old:" + mavenPath.getPath(), oldMetadata),
                new MetadataEnvelope("new" + mavenPath.getPath(), toMetadata(metadata))
            )
        );
        write(mavenPath, updated);
      }
    }
    catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Writes/overwrites metadata, replacing existing one, if any.
   */
  public void replace(final MavenPath mavenPath, final MavenMetadata metadata) {
    checkNotNull(mavenPath);
    checkNotNull(metadata);
    try {
      write(mavenPath, toMetadata(metadata));
    }
    catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Deletes metadata.
   */
  public void delete(final MavenPath mavenPath) {
    checkNotNull(mavenPath);
    try {
      mavenFacet.delete(mavenPath, mavenPath.hash(HashType.SHA1), mavenPath.hash(HashType.MD5));
    }
    catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Converts NX VO into Apache Maven {@link Metadata}.
   */
  private Metadata toMetadata(final MavenMetadata mavenMetadata) {
    final Metadata result = new Metadata();
    result.setModelVersion("1.1.0");
    result.setGroupId(mavenMetadata.getGroupId());
    result.setArtifactId(mavenMetadata.getArtifactId());
    result.setVersion(mavenMetadata.getVersion());
    if (mavenMetadata.getPlugins() != null) {
      for (Plugin plugin : mavenMetadata.getPlugins()) {
        final org.apache.maven.artifact.repository.metadata.Plugin mPlugin = new org.apache.maven.artifact.repository.metadata.Plugin();
        mPlugin.setArtifactId(plugin.getArtifactId());
        mPlugin.setPrefix(plugin.getPrefix());
        mPlugin.setName(plugin.getName());
        result.addPlugin(mPlugin);
      }
    }
    if (mavenMetadata.getBaseVersions() != null) {
      final Versioning versioning = new Versioning();
      versioning.setLatest(mavenMetadata.getBaseVersions().getLatest());
      versioning.setRelease(mavenMetadata.getBaseVersions().getRelease());
      versioning.setVersions(mavenMetadata.getBaseVersions().getVersions());
      versioning.setLastUpdated(dotlessTimestampFormat.format(mavenMetadata.getLastUpdated().toDate()));
      result.setVersioning(versioning);
    }
    if (mavenMetadata.getSnapshots() != null) {
      final Versioning versioning = result.getVersioning() != null ? result.getVersioning() : new Versioning();
      final org.apache.maven.artifact.repository.metadata.Snapshot snapshot = new org.apache.maven.artifact.repository.metadata.Snapshot();
      snapshot.setTimestamp(dottedTimestampFormat.format(
          new DateTime(mavenMetadata.getSnapshots().getSnapshotTimestamp()).toDate()));
      snapshot.setBuildNumber(mavenMetadata.getSnapshots().getSnapshotBuildNumber());
      versioning.setSnapshot(snapshot);

      final List<SnapshotVersion> snapshotVersions = Lists.newArrayList();
      for (Snapshot snap : mavenMetadata.getSnapshots().getSnapshots()) {
        final SnapshotVersion snapshotVersion = new SnapshotVersion();
        snapshotVersion.setExtension(snap.getExtension());
        snapshotVersion.setClassifier(snap.getClassifier());
        snapshotVersion.setVersion(snap.getVersion());
        snapshotVersion.setUpdated(dotlessTimestampFormat.format(snap.getLastUpdated().toDate()));
        snapshotVersions.add(snapshotVersion);
      }
      versioning.setSnapshotVersions(snapshotVersions);
      versioning.setLastUpdated(dotlessTimestampFormat.format(mavenMetadata.getLastUpdated().toDate()));
      result.setVersioning(versioning);
    }
    return result;
  }

  /**
   * Reads up existing metadata and parses it, or {@code null}. If metadata unparseable (corrupted) also {@code null}.
   */
  @Nullable
  private Metadata read(final MavenPath mavenPath) throws IOException {
    final Content content = mavenFacet.get(mavenPath);
    if (content == null) {
      return null;
    }
    else {
      try (InputStream is = content.openInputStream()) {
        return metadataReader.read(is);
      }
      catch (XmlPullParserException e) {
        // corrupted, nuke it
        return null;
      }
    }
  }

  /**
   * Writes passed in metadata as XML.
   */
  private void write(final MavenPath mavenPath, final Metadata metadata)
      throws IOException
  {
    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    metadataWriter.write(byteArrayOutputStream, metadata);
    final Content mainContent = mavenFacet.put(
        mavenPath,
        new BytesPayload(byteArrayOutputStream.toByteArray(), Maven2Format.METADATA_CONTENT_TYPE)
    );
    final Map<HashAlgorithm, HashCode> hashCodes = mainContent.getAttributes().require(
        Content.CONTENT_HASH_CODES_MAP, TypeTokens.HASH_CODES_MAP);
    checkState(hashCodes != null, "hashCodes");
    for (HashType hashType : HashType.values()) {
      final MavenPath checksumPath = mavenPath.hash(hashType);
      final HashCode hashCode = hashCodes.get(hashType.getHashAlgorithm());
      checkState(hashCode != null, "hashCode: type=%s", hashType);
      mavenFacet.put(checksumPath, new StringPayload(hashCode.toString(), Maven2Format.CHECKSUM_CONTENT_TYPE));
    }
  }
}
