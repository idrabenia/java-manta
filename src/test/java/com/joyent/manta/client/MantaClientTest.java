/**
 * Copyright (c) 2013, Joyent, Inc. All rights reserved.
 */
package com.joyent.manta.client;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.BasicConfigurator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.api.client.http.HttpResponseException;
import com.joyent.manta.exception.MantaCryptoException;
import com.joyent.manta.exception.MantaObjectException;

/**
 * @author Yunong Xiao
 */
public class MantaClientTest {

    private static MantaClient CLIENT;

    private static final String URL = "https://us-east.manta.joyent.com";
    private static final String LOGIN = "yunong";
    private static final String KEY_PATH = "src/test/java/data/id_rsa";
    private static final String KEY_FINGERPRINT = "9d:1c:f4:69:66:cb:bf:1a:40:b5:d2:c2:6a:0a:eb:2d";
    private static final String TEST_DATA = "EPISODEII_IS_BEST_EPISODE";
    private static final String TEST_FILE = "src/test/java/data/Master-Yoda.jpg";
    private static final String TEST_DIR_PATH = "/yunong/stor/" + UUID.randomUUID().toString() + "/";

    @BeforeClass
    public static void beforeClass() throws IOException, MantaCryptoException {
        CLIENT = MantaClient.newInstance(URL, LOGIN, KEY_PATH, KEY_FINGERPRINT);
        BasicConfigurator.configure();
        CLIENT.putDirectory(TEST_DIR_PATH, null);
    }

    @Test
    public void testCRUDObject() throws MantaCryptoException, IOException {
        String name = UUID.randomUUID().toString();
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        mantaObject.setDataInputString(TEST_DATA);
        CLIENT.put(mantaObject);
        MantaObject gotObject = CLIENT.get(TEST_DIR_PATH + name);
        String data = MantaUtils.inputStreamToString(gotObject.getDataInputStream());
        assertEquals(mantaObject.getDataInputString(), data);
        CLIENT.delete(mantaObject.getPath());
        boolean thrown = false;
        try {
            CLIENT.get(TEST_DIR_PATH + name);
        } catch (HttpResponseException e) {
            assertEquals(404, e.getStatusCode());
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testCRUDWithFileObject() throws MantaCryptoException, IOException {
        String name = UUID.randomUUID().toString();
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        mantaObject.setDataInputString(TEST_DATA);
        CLIENT.put(mantaObject);
        MantaObject gotObject = CLIENT.get(TEST_DIR_PATH + name);
        File file = new File("/tmp/" + name);
        MantaUtils.inputStreamToFile(gotObject.getDataInputStream(), file);
        String data = FileUtils.readFileToString(file);
        assertEquals(mantaObject.getDataInputString(), data);
        CLIENT.delete(mantaObject.getPath());
        boolean thrown = false;
        try {
            CLIENT.get(TEST_DIR_PATH + name);
        } catch (HttpResponseException e) {
            assertEquals(404, e.getStatusCode());
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testCRUDObjectWithHeaders() throws MantaCryptoException, IOException {
        String name = UUID.randomUUID().toString();
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        mantaObject.setHeader("durability-level", 6);
        mantaObject.setDataInputString(TEST_DATA);
        CLIENT.put(mantaObject);
        MantaObject gotObject = CLIENT.get(TEST_DIR_PATH + name);
        String data = MantaUtils.inputStreamToString(gotObject.getDataInputStream());
        assertEquals(mantaObject.getDataInputString(), data);
        assertEquals(6, mantaObject.getHeader("durability-level"));
        CLIENT.delete(mantaObject.getPath());
        boolean thrown = false;
        try {
            CLIENT.get(TEST_DIR_PATH + name);
        } catch (HttpResponseException e) {
            assertEquals(404, e.getStatusCode());
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testRecursiveDeleteObject() throws MantaCryptoException, HttpResponseException, IOException {
        String name = UUID.randomUUID().toString();
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        mantaObject.setDataInputString(TEST_DATA);
        CLIENT.put(mantaObject);
        CLIENT.deleteRecursive(mantaObject.getPath());

        boolean thrown = true;
        try {
            CLIENT.get(mantaObject.getPath());
        } catch (HttpResponseException e) {
            assertEquals(404, e.getStatusCode());
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testPutWithStream() throws MantaCryptoException, HttpResponseException, IOException {
        String name = UUID.randomUUID().toString();
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        InputStream is = new FileInputStream(new File(TEST_FILE));
        mantaObject.setDataInputStream(is);
        CLIENT.put(mantaObject);
    }

    @Test
    public void testHead() throws MantaCryptoException, IOException {
        String name = UUID.randomUUID().toString();
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        mantaObject.setDataInputString(TEST_DATA);
        CLIENT.put(mantaObject);
        MantaObject obj = CLIENT.head(mantaObject.getPath());
        assertNotNull(obj);
    }

    @Test
    public void testPutLink() throws MantaCryptoException, IOException {
        String name = UUID.randomUUID().toString();
        MantaObject original = new MantaObject(TEST_DIR_PATH + name);
        original.setDataInputString(TEST_DATA);
        CLIENT.put(original);

        String link = UUID.randomUUID().toString();
        CLIENT.putSnapLink(TEST_DIR_PATH + link, TEST_DIR_PATH + name, null);
        MantaObject linkObj = CLIENT.get(TEST_DIR_PATH + link);
        BufferedReader reader = new BufferedReader(new InputStreamReader(linkObj.getDataInputStream()));
        String data = null;
        while ((data = reader.readLine()) != null) {
            assertEquals(TEST_DATA, data);
        }
    }

    @Test
    public void testList() throws MantaCryptoException, IOException, MantaObjectException {
        String pathPrefix = TEST_DIR_PATH + "/" + UUID.randomUUID().toString();
        CLIENT.putDirectory(pathPrefix, null);
        CLIENT.put(new MantaObject(pathPrefix + "/" + UUID.randomUUID().toString()));
        CLIENT.put(new MantaObject(pathPrefix + "/" + UUID.randomUUID().toString()));
        String subDir = pathPrefix + "/" + UUID.randomUUID().toString();
        CLIENT.putDirectory(subDir, null);
        CLIENT.put(new MantaObject(subDir + "/" + UUID.randomUUID().toString()));
        Collection<MantaObject> objs = CLIENT.listObjects(pathPrefix);
        for (MantaObject mantaObject : objs) {
            assertTrue(mantaObject.getPath().startsWith(TEST_DIR_PATH));
        }
        assertEquals(3, objs.size());
    }

    @Test(expected = MantaObjectException.class)
    public void testListNotADir() throws MantaCryptoException, HttpResponseException, IOException, MantaObjectException {
        String name = UUID.randomUUID().toString();
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        mantaObject.setDataInputString(TEST_DATA);
        CLIENT.put(mantaObject);
        CLIENT.listObjects(mantaObject.getPath());
    }
    
    @Test
    public void testRFC3986() throws MantaCryptoException, IOException {
        String name = "spaces in the name of the file";
        MantaObject mantaObject = new MantaObject(TEST_DIR_PATH + name);
        mantaObject.setDataInputString(TEST_DATA);
        CLIENT.put(mantaObject);
        MantaObject gotObject = CLIENT.get(TEST_DIR_PATH + name);
        String data = MantaUtils.inputStreamToString(gotObject.getDataInputStream());
        assertEquals(mantaObject.getDataInputString(), data);
        CLIENT.delete(mantaObject.getPath());
        boolean thrown = false;
        try {
            CLIENT.get(TEST_DIR_PATH + name);
        } catch (HttpResponseException e) {
            assertEquals(404, e.getStatusCode());
            thrown = true;
        }
        assertTrue(thrown);
    }

    @AfterClass
    public static void afterClass() throws MantaCryptoException, HttpResponseException, IOException {
        CLIENT.deleteRecursive(TEST_DIR_PATH);
    }
}