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
package org.sonatype.security.realms.ldap.client;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * LDAP Server connection configuration.
 *
 * @sinnce 3.0
 */
public final class Connection
{
  public enum Protocol
  {
    ldap, ldaps
  }

  public static final class Host
  {
    private Protocol protocol;

    private String hostName;

    private int port;

    public Host(final Protocol protocol, final String hostName, final int port) {
      setProtocol(protocol);
      setHostName(hostName);
      setPort(port);
    }

    public Protocol getProtocol() {
      return protocol;
    }

    public void setProtocol(final Protocol protocol) {
      this.protocol = checkNotNull(protocol);
    }

    public String getHostName() {
      return hostName;
    }

    public void setHostName(final String hostName) {
      this.hostName = checkNotNull(hostName);
    }

    public int getPort() {
      return port;
    }

    public void setPort(final int port) {
      checkArgument(port > 0, "Invalid LDAP port: %s", port);
      this.port = port;
    }

    @Override
    public String toString() {
      return "Host{" +
          "protocol=" + protocol +
          ", hostName='" + hostName + '\'' +
          ", port=" + port +
          '}';
    }
  }

  /**
   * Search Base. Base DN for the connection.
   */
  private String searchBase;

  /**
   * System User. The username of user with access to the LDAP server.
   */
  @Nullable
  private String systemUsername;

  /**
   * System Password. The password for the System User.
   */
  @Nullable
  private String systemPassword;

  /**
   * Authentication Scheme. Method used for authentication: none, simple, etc.
   */
  private String authScheme;

  /**
   * The LDAP server host.
   */
  private Host host;

  /**
   * The LDAP backup mirror host.
   */
  @Nullable
  private Host backupHost;

  /**
   * SASL Realm. The authentication realm.
   */
  @Nullable
  private String saslRealm;

  /**
   * Connection timeout. Connection timeout in seconds.
   */
  private int connectionTimeout;

  /**
   * Connection retry delay. Connection retry delay in seconds.
   */
  private int connectionRetryDelay;

  /**
   * Max incidents count, before connection is blacklisted.
   */
  private int maxIncidentsCount;

  public String getSearchBase() {
    return searchBase;
  }

  public void setSearchBase(final String searchBase) {
    this.searchBase = checkNotNull(searchBase);
  }

  public String getSystemUsername() {
    return systemUsername;
  }

  public void setSystemUsername(final String systemUsername) {
    this.systemUsername = systemUsername;
  }

  public String getSystemPassword() {
    return systemPassword;
  }

  public void setSystemPassword(final String systemPassword) {
    this.systemPassword = systemPassword;
  }

  public String getAuthScheme() {
    return authScheme;
  }

  public void setAuthScheme(final String authScheme) {
    this.authScheme = authScheme;
  }

  public Host getHost() {
    return host;
  }

  public void setHost(final Host host) {
    this.host = checkNotNull(host);
  }

  @Nullable
  public Host getBackupHost() {
    return backupHost;
  }

  public void setBackupHost(final @Nullable Host backupHost)
  {
    this.backupHost = backupHost;
  }

  public String getSaslRealm() {
    return saslRealm;
  }

  public void setSaslRealm(final String realm) {
    this.saslRealm = realm;
  }

  public int getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(final int connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public int getConnectionRetryDelay() {
    return connectionRetryDelay;
  }

  public void setConnectionRetryDelay(final int connectionRetryDelay) {
    this.connectionRetryDelay = connectionRetryDelay;
  }

  public int getMaxIncidentsCount() {
    return maxIncidentsCount;
  }

  public void setMaxIncidentsCount(final int maxIncidentsCount) {
    this.maxIncidentsCount = maxIncidentsCount;
  }
}
