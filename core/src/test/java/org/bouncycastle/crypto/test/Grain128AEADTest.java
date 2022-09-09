package org.bouncycastle.crypto.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.Grain128AEADEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.test.SimpleTest;

public class Grain128AEADTest
    extends SimpleTest
{
    public String getName()
    {
        return "Grain-128AEAD";
    }

    public void performTest()
        throws Exception
    {
        testVectors();
        testSplitUpdate();
        testExceptions();
    }

    private void testVectors()
        throws Exception
    {
        Grain128AEADEngine grain = new Grain128AEADEngine();
        CipherParameters params;
        InputStream src = Grain128AEADTest.class.getResourceAsStream("/org/bouncycastle/crypto/test/LWC_AEAD_KAT_128_96.txt");
        BufferedReader bin = new BufferedReader(new InputStreamReader(src));
        String line;
        String[] data;
        byte[] ptByte, adByte;
        byte[] rv;
        HashMap<String, String> map = new HashMap<String, String>();
        while ((line = bin.readLine()) != null)
        {
            data = line.split(" ");
            if (data.length == 1)
            {
                params = new ParametersWithIV(new KeyParameter(Hex.decode(map.get("Key"))), Hex.decode(map.get("Nonce")));
                grain.init(true, params);
                adByte = Hex.decode(map.get("AD"));
                grain.processAADBytes(adByte, 0, adByte.length);
                ptByte = Hex.decode(map.get("PT"));
                rv = new byte[ptByte.length];
                grain.processBytes(ptByte, 0, ptByte.length, rv, 0);
                byte[] mac = new byte[8];
                grain.doFinal(mac, 0);
                if (!areEqual(Arrays.concatenate(rv, mac), Hex.decode(map.get("CT"))))
                {
                    mismatch("Keystream " + map.get("Count"), map.get("CT"), rv);
                }
                map.clear();
            }
            else
            {
                if (data.length >= 3)
                {
                    map.put(data[0].trim(), data[2].trim());
                }
                else
                {
                    map.put(data[0].trim(), "");
                }
            }
        }
    }

    private void testSplitUpdate()
        throws InvalidCipherTextException
    {
        byte[] Key = Hex.decode("000102030405060708090A0B0C0D0E0F");
        byte[] Nonce = Hex.decode("000102030405060708090A0B");
        byte[] PT = Hex.decode("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
        byte[] AD = Hex.decode("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E");
        byte[] CT = Hex.decode("EAD60EF559493ACEF6A3C238C018835DE3ABB6AA621A9AA65EFAF7B9D05BBE6C0913DFC8674BACC9");

        Grain128AEADEngine grain = new Grain128AEADEngine();
        ParametersWithIV params = new ParametersWithIV(new KeyParameter(Key), Nonce);
        grain.init(true, params);

        grain.processAADBytes(AD, 0, 10);
        grain.processAADByte(AD[10]);
        grain.processAADBytes(AD, 11, AD.length - 11);

        byte[] rv = new byte[CT.length];
        int len = grain.processBytes(PT, 0, 10, rv, 0);
        len += grain.processByte(PT[10], rv, len);
        len += grain.processBytes(PT, 11, PT.length - 11, rv, len);

        grain.doFinal(rv, len);

        isTrue(Arrays.areEqual(rv, CT));

        grain.processBytes(PT, 0, 10, rv, 0);
        try
        {
            grain.processAADByte((byte)0x01);
            fail("no exception");
        }
        catch (IllegalStateException e)
        {
            isEquals("associated data must be added before plaintext/ciphertext", e.getMessage());
        }

        try
        {
            grain.processAADBytes(AD, 0, AD.length);
            fail("no exception");
        }
        catch (IllegalStateException e)
        {
            isEquals("associated data must be added before plaintext/ciphertext", e.getMessage());
        }
    }

    private void testExceptions()
        throws InvalidCipherTextException
    {
        try
        {
            Grain128AEADEngine grain128 = new Grain128AEADEngine();

            grain128.init(true, new KeyParameter(new byte[10]));
            fail("no exception");
        }
        catch (IllegalArgumentException e)
        {
            isEquals("Grain-128AEAD init parameters must include an IV", e.getMessage());
        }

        try
        {
            Grain128AEADEngine grain128 = new Grain128AEADEngine();

            grain128.init(true, new ParametersWithIV(new KeyParameter(new byte[10]), new byte[8]));
            fail("no exception");
        }
        catch (IllegalArgumentException e)
        {
            isEquals("Grain-128AEAD requires exactly 12 bytes of IV", e.getMessage());
        }

        try
        {
            Grain128AEADEngine grain128 = new Grain128AEADEngine();

            grain128.init(true, new ParametersWithIV(new KeyParameter(new byte[10]), new byte[12]));
            fail("no exception");
        }
        catch (IllegalArgumentException e)
        {
            isEquals("Grain-128AEAD key must be 128 bits long", e.getMessage());
        }
    }

    private void mismatch(String name, String expected, byte[] found)
    {
        fail("mismatch on " + name, expected, new String(Hex.encode(found)));
    }

    public static void main(String[] args)
    {
        runTest(new Grain128AEADTest());
    }
}

