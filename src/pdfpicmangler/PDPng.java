/*  This file is part of PDFPicMangler, an image resampling tool for pdf documents. 
 *  Copyright (C) 2017  Ingo Kresse
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package pdfpicmangler;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.CRC32;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDIndexed;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;


//TODO: create file from given data
//TODO: parse and translate transparency chuck

public class PDPng extends PDXObjectImage
{
    private static final byte[] PNG_MAGIC = { (byte) 137, (byte) 80, (byte) 78, (byte) 71, (byte) 13, (byte) 10,
            (byte) 26, (byte) 10 };
    
    private static final int PNG_TYPE_PALETTE = 1;
    private static final int PNG_TYPE_COLOR = 2;
    private static final int PNG_TYPE_ALPHA_CHANNEL = 4;
    
    private CRC32 checksum; 
    
    //private PDIndexed palette;
    private COSArray paldata;
    
    private OutputStream data;

    private int dataLen;

    public PDPng(PDStream pngStream)
    {
        super(pngStream, "png");
    }

    /**
     * Construct from a stream.
     * 
     * @param doc The document to create the image as part of.
     * @param is The stream that contains the png data.
     * @throws IOException If there is an error reading the png data.
     */
    public PDPng(PDDocument doc, InputStream is) throws IOException
    {
        super(doc, "png");
        
        System.out.println("reading in png");

        COSDictionary dic = getCOSStream();
        
        dic.setItem(COSName.SUBTYPE, COSName.IMAGE);
        //dic.setItem(COSName.TYPE, COSName.XOBJECT);
        
        data = getCOSStream().createFilteredStream();

        readPng(is);
        
        setWidth(imageWidth);
        setHeight(imageHeight);
        dic.setInt(COSName.BITS_PER_COMPONENT, bitDepth);

        if((colorType & PNG_TYPE_PALETTE) != 0)
        {
            getCOSStream().setItem( COSName.COLORSPACE, paldata );
        } 
        else if ((colorType & PNG_TYPE_COLOR) != 0)
        {
            setColorSpace( PDDeviceRGB.INSTANCE );
        }
        else
        {
            setColorSpace( new PDDeviceGray() );
        }
        
        COSDictionary filterParams = new COSDictionary();
        filterParams.setInt(COSName.PREDICTOR, 15); // png adaptive predictor
        filterParams.setInt(COSName.COLORS, ((colorType & PNG_TYPE_COLOR) == 0 || (colorType & PNG_TYPE_PALETTE) != 0) ? 1 : 3);
        filterParams.setInt(COSName.BITS_PER_COMPONENT, bitDepth);
        filterParams.setInt(COSName.COLUMNS, imageWidth);
        filterParams.setDirect(true);
        
        dic.setItem(COSName.DECODE_PARMS, filterParams);
        dic.setItem(COSName.FILTER, COSName.FLATE_DECODE);
        
        dic.setInt(COSName.LENGTH, dataLen);
        dic.getDictionaryObject(COSName.LENGTH).setDirect(true);
    }

    private boolean readPng(InputStream is)
    {
        checksum = new CRC32();
        dataLen = 0;
        
        try
        {
            boolean ok = readMagic(is);
            
            if(!ok)
            {
                throw new IOException("png magic not found.");
            }

            while (true)
            {
                readChunkHeader(is);
                
                System.out.println(chunkType + " chunk, " + chunkLen + " bytes"); 
                
                if ("IHDR".equals(chunkType))
                {
                    readHeader(is);
                    readChunkChecksum(is);
                    continue;
                }
                if ("PLTE".equals(chunkType))
                {
                    readPalette(is);
                    readChunkChecksum(is);
                    continue;
                }
                if ("IDAT".equals(chunkType))
                {
                    readData(is);
                    readChunkChecksum(is);
                    dataLen += chunkLen;
                    continue;
                }
                if ("IEND".equals(chunkType))
                {
                    readEnd(is);
                    readChunkChecksum(is);
                    break;
                }
                
                // unknown chunk type, eat the data
                readBytes(is, chunkLen+4);
            }

        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void readEnd(InputStream is) throws IOException
    {
        System.out.println("got end");
        data.close();
    }

    private void readData(InputStream is) throws IOException
    {
        // read chunkLen bytes into data stream
        byte[] buffer = new byte[1024];
        
        int bytesLeft = chunkLen;
        int len = (bytesLeft > buffer.length) ? buffer.length : bytesLeft;
        int amountRead = -1;
        while ((amountRead = is.read(buffer, 0, len)) != -1 && bytesLeft > 0)
        {
            data.write(buffer, 0, amountRead);
            bytesLeft -= amountRead;
            len = (bytesLeft > buffer.length) ? buffer.length : bytesLeft;
            
            checksum.update(buffer, 0, amountRead);
        }
        System.out.println("got " + chunkLen + " bytes of data");
    }

    private void readPalette(InputStream is) throws IOException
    {
        if(chunkLen > 256*3 || (chunkLen % 3) != 0)
        {
            throw new IOException("unexpected palette length " + chunkLen);
        }

        COSString data = new COSString(readBytes(is, chunkLen));
        
        // for some reason, readers expect an extra entry...
        byte[] extra = {0,0,0};
        data.append(extra);
        
        int numEntries = chunkLen / 3;
        
        paldata = new COSArray();
        paldata.add(COSName.getPDFName(PDIndexed.NAME));
        paldata.add(COSName.getPDFName(PDDeviceRGB.NAME));
        paldata.add(COSInteger.get(numEntries));
        paldata.add(data);
        
        System.out.println("got palette with " + numEntries + " entries.");
    }

    private String chunkType;
    private int chunkLen;

    private int imageHeight;
    private int imageWidth;
    private int bitDepth;

    private int colorType;

    private void readChunkHeader(InputStream is) throws IOException
    {
        chunkLen = readInt32(is);
        checksum.reset();
        chunkType = readType(is);
    }
    
    private void readChunkChecksum(InputStream is) throws IOException
    {
        int chkComputed = (int) checksum.getValue();
        int chkFile = readInt32(is);
        
        if(chkFile != chkComputed)
        {
            System.out.println("crc error: computed \n  0x" + Integer.toHexString(chkComputed) + " but found\n  0x" + Integer.toHexString(chkFile));
            //throw...
        }
        else
        {
            System.out.println("crc ok: 0x" + Integer.toHexString(chkFile));
        }
    }

    private String readType(InputStream is) throws IOException
    {
        return new String(readBytes(is, 4));
    }

    private byte[] readBytes(InputStream is, int len) throws IOException
    {
        byte[] buffer = new byte[len];
        int amountRead = is.read(buffer);
        if (amountRead != len)
        {
            throw new IOException();
        }
        
        checksum.update(buffer);
        
        return buffer;
    }

    private int readInt32(InputStream is) throws IOException
    {
        byte[] buffer = readBytes(is, 4);
        return ((0xff & buffer[0]) << 24) | ((0xff & buffer[1]) << 16) | ((0xff & buffer[2]) << 8) | (0xff &  buffer[3]);
    }
    
    private int readInt8(InputStream is) throws IOException
    {
        byte[] buffer = readBytes(is, 1);
        return buffer[0];
    }

    private boolean readMagic(InputStream is) throws IOException
    {
        byte[] buffer = readBytes(is, 8);
        return Arrays.equals(buffer, PNG_MAGIC);
    }

    private void readHeader(InputStream is) throws IOException
    {
        if(chunkLen != 13)
        {
            throw new IOException("unexpected length for IHDR chunk!");
        }
        
        imageWidth = readInt32(is);
        imageHeight = readInt32(is);
        bitDepth = readInt8(is);
        colorType = readInt8(is);
        
        int compressionMethod = readInt8(is);
        int filterMethod = readInt8(is);
        int interlaceMethod = readInt8(is);
        
        if(filterMethod != 0)
        {
            throw new IOException("unexpected filter method " + filterMethod);
        }
        
        if(compressionMethod != 0)
        {
            throw new IOException("unexpected compression method " + compressionMethod);
        }
        
        if(interlaceMethod != 0)
        {
            throw new IOException("only non-interlaced PNGs are supported in pdf. Needs recompression...");
        }
        
        if((colorType & PNG_TYPE_ALPHA_CHANNEL) != 0)
        {
            throw new IOException("pdf does not support 'embedded' alpha channel. Needs recompression...");
        }

        System.out.println("got header");
    }

    @Override
    public BufferedImage getRGBImage() throws IOException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void write2OutputStream(OutputStream out) throws IOException
    {
        // TODO Auto-generated method stub

    }

}
