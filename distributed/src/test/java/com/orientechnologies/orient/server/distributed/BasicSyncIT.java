package com.orientechnologies.orient.server.distributed;

import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.test.TestConfig;
import com.orientechnologies.orient.test.TestSetup;
import com.orientechnologies.orient.test.TestSetupUtil;
import com.orientechnologies.orient.test.configs.SimpleDServerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BasicSyncIT {

  private TestSetup setup;
  private TestConfig config;
  private String server0, server1, server2;

  @Before
  public void before() throws Exception {
    config = new SimpleDServerConfig();
    server0 = SimpleDServerConfig.SERVER0;
    server1 = SimpleDServerConfig.SERVER1;
    server2 = SimpleDServerConfig.SERVER2;
    setup = TestSetupUtil.create(config);
    setup.start();

    OrientDB remote = setup.createRemote(server0, "root", "test", OrientDBConfig.defaultConfig());
    remote.create("test", ODatabaseType.PLOCAL);
    remote.close();
  }

  @Test
  public void sync() {
    try (OrientDB remote = setup.createRemote(server0, OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        session.createClass("One");
        session.save(session.newElement("One"));
        session.save(session.newElement("One"));
      }
      //      // TODO: Without this sleep, test fails in a distributed setup!
      //      try {
      //        Thread.sleep(10000);
      //      } catch (InterruptedException e) {
      //      }
      setup.shutdownServer(server2);
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        session.save(session.newElement("One"));
      }
    }
    setup.shutdownServer(server0);
    setup.shutdownServer(server1);

    setup.startServer(server0);
    setup.startServer(server1);
    setup.startServer(server2);
    // Test server 0
    try (OrientDB remote = setup.createRemote(server0, OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        assertEquals(session.countClass("One"), 3);
      }
    }
    // Test server 1
    try (OrientDB remote = setup.createRemote(server1, OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        assertEquals(session.countClass("One"), 3);
      }
    }
    // Test server 2
    try (OrientDB remote = setup.createRemote(server2, OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        assertEquals(session.countClass("One"), 3);
      }
    }
  }

  @Test
  public void reverseStartSync() {
    try (OrientDB remote = setup.createRemote(server0, OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        session.createClass("One");
        session.save(session.newElement("One"));
        session.save(session.newElement("One"));
      }
      setup.shutdownServer(server2);
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        session.save(session.newElement("One"));
      }
    }
    setup.shutdownServer(server1);
    setup.shutdownServer(server0);
    // Starting the servers in reverse shutdown order to trigger miss sync
    setup.startServer(server2);
    setup.startServer(server1);
    setup.startServer(server0);
    // Test server 0
    try (OrientDB remote = new OrientDB("remote:localhost", OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        assertEquals(session.countClass("One"), 3);
      }
    }
    // Test server 1
    try (OrientDB remote = new OrientDB("remote:localhost:2425", OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        assertEquals(session.countClass("One"), 3);
      }
    }
    // Test server 2
    try (OrientDB remote = new OrientDB("remote:localhost:2426", OrientDBConfig.defaultConfig())) {
      try (ODatabaseSession session = remote.open("test", "admin", "admin")) {
        assertEquals(session.countClass("One"), 3);
      }
    }
  }

  @After
  public void after() {
    try {
      OrientDB remote = setup.createRemote(server0, "root", "test", OrientDBConfig.defaultConfig());
      remote.drop("test");
      remote.close();
    } finally {
      setup.teardown();
      ODatabaseDocumentTx.closeAll();
    }
  }
}
