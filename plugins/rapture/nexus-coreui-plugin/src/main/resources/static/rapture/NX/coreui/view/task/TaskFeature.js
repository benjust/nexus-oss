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
/*global Ext, NX*/

/**
 * Task feature panel.
 *
 * @since 3.0
 */
Ext.define('NX.coreui.view.task.TaskFeature', {
  extend: 'NX.view.drilldown.Drilldown',
  alias: 'widget.nx-coreui-task-feature',
  requires: [
    'NX.I18n'
  ],

  iconName: 'task-default',

  masters: { xtype: 'nx-coreui-task-list' },

  tabs: [
    {
      xtype: 'nx-info-panel',
      title: NX.I18n.get('ADMIN_TASKS_DETAILS_SUMMARY_TAB'),
      weight: 10
    }
  ],

  actions: [
    {
      xtype: 'button',
      text: NX.I18n.get('ADMIN_TASKS_DETAILS_DELETE_BUTTON'),
      glyph: 'xf056@FontAwesome' /* fa-minus-circle */,
      action: 'delete',disabled: true
    },
    {
      xtype: 'button',
      text: NX.I18n.get('ADMIN_TASKS_DETAILS_RUN_BUTTON'),
      glyph: 'xf04b@FontAwesome' /* fa-play */,
      action: 'run',
      handler: function(button) { button.fireEvent('runaction') },
      disabled: true
    },
    {
      xtype: 'button',
      text: NX.I18n.get('ADMIN_TASKS_DETAILS_STOP_BUTTON'),
      glyph: 'xf04d@FontAwesome' /* fa-stop */,
      action: 'stop',
      handler: function(button) { button.fireEvent('runaction') },
      disabled: true
    }
  ]
});
