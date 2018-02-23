package org.odk.collect.android.utilities;

import android.graphics.Bitmap;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.WriterException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.odk.collect.android.application.Collect;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DataFormatException;

import timber.log.Timber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.odk.collect.android.utilities.QRCodeUtils.MD5_CACHE_PATH;
import static org.odk.collect.android.utilities.QRCodeUtils.QR_CODE_FILEPATH;


@RunWith(RobolectricTestRunner.class)
public class QRCodeUtilsTest {

    private final File savedFile = new File(QR_CODE_FILEPATH);
    private final File md5File = new File(MD5_CACHE_PATH);
    private final LogStackTree logStackTree = new LogStackTree();

    @Before
    public void setup() {
        Timber.plant(logStackTree);
        logStackTree.clear();

        savedFile.delete();
        md5File.delete();
    }

    @Test
    public void generateAndDecodeQRCode() throws IOException, WriterException, DataFormatException, ChecksumException, NotFoundException, FormatException {
        String data = "Some random text";
        Bitmap generatedQRBitMap = QRCodeUtils.generateQRBitMap(data, 100);
        assertQRContains(generatedQRBitMap, data);
    }

    @Test
    public void generateQRCodeIfNoCacheExists() throws DataFormatException, FormatException, ChecksumException, NotFoundException, IOException, NoSuchAlgorithmException {
        AtomicBoolean isFinished = new AtomicBoolean(false);
        AtomicReference<Bitmap> generatedBitmap = new AtomicReference<>();
        AtomicReference<Throwable> errorThrown = new AtomicReference<>();
        ArrayList<String> list = new ArrayList<>();

        // verify that QRCode and md5 cache files don't exist
        assertFalse(savedFile.exists());
        assertFalse(md5File.exists());

        // subscribe to the QRCode generator in the same thread
        QRCodeUtils.getQRCodeGeneratorObservable(list)
                .subscribe(generatedBitmap::set, errorThrown::set, () -> isFinished.set(true));

        assertNotNull(generatedBitmap.get());
        assertNull(errorThrown.get());
        assertTrue(isFinished.get());

        assertGeneratedNewFiles();

        String expectedData = "{\"general\":{},\"admin\":{}}";
        assertQRContains(generatedBitmap.get(), expectedData);

        // verify that the md5 data in the cached file is correct
        assertCachedFileIsCorrect(expectedData.getBytes(), md5File);
    }

    @Test
    public void readQRCodeFromDiskIfCacheExists() throws NoSuchAlgorithmException, IOException, WriterException, DataFormatException, ChecksumException, NotFoundException, FormatException {
        String expectedData = "{\"general\":{},\"admin\":{}}";

        // stubbing cache and bitmap files
        new File(Collect.SETTINGS).mkdirs();
        FileUtils.saveBitmapToFile(QRCodeUtils.generateQRBitMap(expectedData, 100), QR_CODE_FILEPATH);
        FileUtils.write(md5File, getDigest(expectedData.getBytes()));

        // verify that QRCode and md5 cache files exist
        assertTrue(savedFile.exists());
        assertTrue(md5File.exists());

        AtomicBoolean isFinished = new AtomicBoolean(false);
        AtomicReference<Bitmap> generatedBitmap = new AtomicReference<>();
        AtomicReference<Throwable> errorThrown = new AtomicReference<>();
        ArrayList<String> list = new ArrayList<>();

        // subscribe to the QRCode generator in the same thread
        QRCodeUtils.getQRCodeGeneratorObservable(list)
                .subscribe(generatedBitmap::set, errorThrown::set, () -> isFinished.set(true));

        assertNotNull(generatedBitmap.get());
        assertNull(errorThrown.get());
        assertTrue(isFinished.get());

        assertRestoredFromCache();

        // verify that the md5 data in the cached file is correct
        assertCachedFileIsCorrect(expectedData.getBytes(), md5File);
    }

    private void assertCachedFileIsCorrect(byte[] data, File file) throws NoSuchAlgorithmException {
        byte[] messageDigest = getDigest(data);
        byte[] cachedMessageDigest = FileUtils.read(file);
        assertTrue(Arrays.equals(messageDigest, cachedMessageDigest));
    }

    private byte[] getDigest(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(data);
        return md.digest();
    }

    private void assertQRContains(Bitmap bitmap, String data) throws DataFormatException, FormatException, ChecksumException, NotFoundException, IOException {
        assertNotNull(bitmap);
        String result = QRCodeUtils.decodeFromBitmap(bitmap);
        assertEquals(data, result);
    }

    private void assertGeneratedNewFiles() {
        assertEquals(logStackTree.pop(), "Updated .md5 file contents");
        assertEquals(logStackTree.pop(), "Saving QR Code to disk... : " + QR_CODE_FILEPATH);

        assertTrue(savedFile.exists());
        assertTrue(md5File.exists());
    }

    private void assertRestoredFromCache() {
        assertEquals(logStackTree.pop(), "Loading QRCode from the disk...");

        assertTrue(savedFile.exists());
        assertTrue(md5File.exists());
    }
}
