/**
 *  Copyright (C) 2002-2012   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */


package net.sf.freecol.client.gui.sound;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.jcraft.jogg.*;
import com.jcraft.jorbis.*;


/**
 * Rewritten from the JCraft JOrbisPlayer example (GPLv2+), fixing a
 * bunch of bugs that prevent it from playing short files and recasting
 * into a read()-driven AudioInputStream form.
 *
 * FreeCol has a few short files.  We can notice when this fails.
 */
public class OggVorbisDecoderFactory {

    /**
     * Core JOgg/JOrbis magic handled here.
     */
    private class OggStream extends InputStream {

        private Packet oggPacket = new Packet();
        private Page oggPage = new Page();
        private StreamState oggStreamState = new StreamState();
        private SyncState oggSyncState = new SyncState();
        private DspState orbisDspState = new DspState();
        private Block orbisBlock = new Block(orbisDspState);
        private Comment orbisComment = new Comment();
        private Info orbisInfo = new Info();

        private AudioFormat audioFormat;

        // End of stream marker.
        private final String EOS = "End-of-stream";

        // Internal buffer size.
        private final int BUFSIZ = 4096;
        
        // The buffer to convert into.
        private byte[] convBuf = new byte[BUFSIZ];
        // The amount of data waiting in the buffer.
        private int bufCount = 0;
        // The position in the buffer of the data.
        private int offset = 0;
        
        // PCM index and data.
        private int[] pcmi;
        private float[][][] pcmData;

        // The stream containing the data.
        private InputStream inputStream = null;


        public OggStream(InputStream inputStream) throws IOException {
            super();
            this.inputStream = inputStream;
            String err = getHeader();
            if (err != null) throw new IOException(err);
            this.audioFormat = new AudioFormat(orbisInfo.rate,
                16, // bits per sample
                orbisInfo.channels,
                true, // signed
                false); // little endian
            this.bufCount = 0;
            this.offset = 0;
        }

        public void close() {
            oggSyncState.clear();
            oggStreamState.clear();
            orbisBlock.clear();
            orbisDspState.clear();
        }

        public AudioFormat getFormat() {
            return audioFormat;
        }

        /**
         * Gets the amount of data available to be read right now.
         *
         * @return A number of bytes to read.
         */
        public int available() {
            return bufCount;
        }

        public int read() throws IOException {
            byte[] b = new byte[1];
            return (read(b) > 0) ? b[0] : -1;
        }

        public int read(byte[] buf) throws IOException {
            return read(buf, buf.length);
        }

        /**
         * Reads into the supplied buffer.
         *
         * @param buf The buffer to read to.
         * @param n The number of bytes to read.
         * @return Negative on error, zero on end of stream, otherwise the
         *     number of bytes added to the buffer.
         * @throws IOException if JOrbis loses.
         */
        public int read(byte[] buf, int n) throws IOException {
            int wr = 0, wrOffset = 0;
            while (n > 0) {
                if (bufCount <= 0) {
                    int ret = getBody(inputStream);
                    if (ret < 0) throw new IOException("Ogg decoding error");
                    if (ret == 0) break;
                    bufCount = ret;
                    offset = 0;
                }
                int rd = (bufCount < n) ? bufCount : n;
                System.arraycopy(convBuf, offset, buf, wrOffset, rd);
                bufCount -= rd;
                offset += rd;
                wr += rd;
                wrOffset += rd;
                n -= rd;
            }
            return (wr <= 0) ? -1 : wr;
        }

        /**
         * Skips a number of bytes.
         *
         * @param n The number of bytes to skip.
         * @return The actual number of bytes skipped.
         */
        public long skip(long n) throws IOException {
            long wr = 0;
            while (n > 0) {
                if (bufCount <= 0) {
                    int ret = getBody(inputStream);
                    if (ret < 0) throw new IOException("Ogg decoding error");
                    if (ret == 0) break;
                    bufCount = ret;
                    offset = 0;
                }
                long rd = (bufCount < n) ? bufCount : n;
                bufCount -= rd;
                offset += rd;
                wr += rd;
                n -= rd;
            }
            return wr;
        }

        public void mark(int readLimit) {}
        public boolean markSupported() { return false; }
        public void reset() {}

        /**
         * Gets the OGG header (first three packets) which must contain vorbis
         * audio content.
         * This routine is public so it can be used as a check if the a file
         * really does contain vorbis.
         *
         * @return An error message if a page not is available, null on success.
         */
        private String getHeader() {
            String input;
            int packet = 0;

            oggSyncState.init();

            // Special handling for first packet--- we need the oggPage
            // to be read before the oggStreamState can be initialized.
            while (packet < 1) {
                switch (oggSyncState.pageout(oggPage)) {
                case 1:
                    oggStreamState.init(oggPage.serialno());
                    oggStreamState.reset();
                    
                    // Initializes the Info and Comment objects.
                    orbisInfo.init();
                    orbisComment.init();
                    
                    // Check the page (serial number and stuff).
                    if (oggStreamState.pagein(oggPage) == -1) {
                        return "Error on header page";
                    }
                    
                    // Extract first packets.
                    if (oggStreamState.packetout(oggPacket) != 1) {
                        return "Error on first packet";
                    }
                    if (orbisInfo.synthesis_headerin(orbisComment, oggPacket)
                        < 0) {
                        return "Non-vorbis data found";
                    }
                    packet = 1;
                    break;
                case 0:
                    if ((input = getInput()) != null) return input;
                    break;
                default:
                    return "Error reading first page";
                }
            }

            // Read another two packets to complete the header.
            while (packet < 3) {
                //System.err.println("At " + packet);
                switch (oggStreamState.packetout(oggPacket)) {
                case 1:
                    orbisInfo.synthesis_headerin(orbisComment, oggPacket);
                    packet++;
                    break;
                case 0:
                    if ((input = getPage()) != null) return input;
                    break;
                default:
                    return "Error in header packet " + packet;
                }
            }
            orbisDspState.synthesis_init(orbisInfo);
            orbisBlock.init(orbisDspState);
            return null;
        }

        /**
         * Gets another chunk of input into the oggSyncState.
         *
         * @return An error message if input is not available, null on success.
         */
        private String getInput() {
            int count = -1;
            try {
                int idx = oggSyncState.buffer(BUFSIZ);
                count = inputStream.read(oggSyncState.data, idx, BUFSIZ);
            } catch (IOException e) {
                return e.getMessage();
            }
            if (count > 0) oggSyncState.wrote(count);
            return (count > 0) ? null : EOS;
        }

        /**
         * Gets the next page from the oggSyncState into the oggStreamState.
         *
         * @return An error message if a page not is available, null on success.
         */
        private String getPage() {
            String input;
            for (;;) {
                switch (oggSyncState.pageout(oggPage)) { 
                case 0:
                    if ((input = getInput()) != null) return input;
                    break;
                case 1:
                    oggStreamState.pagein(oggPage);
                    return null;
                default:
                    return "Bogus page";
                }
            }
        }
        
        /**
         * Refills the conversion buffer.
         *
         * @return The number of bytes waiting in the convBuf.
         */
        public int getBody(InputStream is) {
            String err;
            int packet = 3;

            pcmi = new int[orbisInfo.channels];
            pcmData = new float[1][][];
            for (;;) {
                switch (oggStreamState.packetout(oggPacket)) {
                case 1:
                    if (orbisBlock.synthesis(oggPacket) == 0) {
                        orbisDspState.synthesis_blockin(orbisBlock);
                    }
                    for (;;) {
                        int n = orbisDspState.synthesis_pcmout(pcmData, pcmi);
                        if (n <= 0) break;
                        orbisDspState.synthesis_read(n);
                        return 2 * orbisInfo.channels * decodePacket(n);
                    }
                    packet++;
                    break;
                case 0:
                    if ((err = getPage()) != null) {
                        return (EOS.equals(err)) ? 0 : -1;
                    }
                    break;
                default:
                    return -1;
                }
            }
        }

        /**
         * Decode the PCM data.
         *
         * @return The number of bytes waiting in the conversion buffer to
         *     be written.
         */
        private int decodePacket(int samples) {
            int range = (samples < convBuf.length) ? samples : convBuf.length;
            for (int i = 0; i < orbisInfo.channels; i++) {
                int sampleIndex = i * 2;
                for (int j = 0; j < range; j++) {
                    // Retrieve the PCM
                    int value = (int)(pcmData[0][i][pcmi[i] + j] * 32767.0f);
                    // Clip to signed 16 bit
                    if (value > 32767) value = 32767;
                    else if (value < -32768) value = -32768;
                    // Stuff into the conversion buffer, little endian
                    convBuf[sampleIndex] = (byte)(value);
                    convBuf[sampleIndex + 1] = (byte)(value >>> 8);
                    // Jump forward (interleaving channels)
                    sampleIndex += 2 * (orbisInfo.channels);
                }
            }
            return range;
        }
    };

    /**
     * The AudioInputStream extension to handle decoding Ogg/Vorbis Audio
     * input.
     */
    private class OggVorbisAudioInputStream extends AudioInputStream {

        // Core JOgg and JOrbis magic.
        private OggStream os = null;


        /**
         * Create a new player.
         *
         * @param inputStream The stream to read from.
         */
        public OggVorbisAudioInputStream(OggStream os) throws IOException {
            super(os, os.getFormat(), AudioSystem.NOT_SPECIFIED);
            this.os = os;
        }

        public AudioFormat getFormat() {
            return os.getFormat();
        }

        public long getFrameLength() {
            return frameLength;
        }

        public int available() {
            return os.available();
        }

        public int read() throws IOException {
            return os.read();
        }

        public int read(byte[] buf) throws IOException {
            return os.read(buf);
        }

        public int read(byte[] buf, int n) throws IOException {
            return os.read(buf, n);
        }

        public void close() {
            os.close();
        }

        public long skip(long n) throws IOException {
            return os.skip(n);
        }

        public void mark(int readLimit) {
            os.mark(readLimit);
        }

        public boolean markSupported() {
            return os.markSupported();
        }

        public void reset() {
            os.reset();
        }
    };


    /**
     * Trivial constructor.
     */
    public OggVorbisDecoderFactory() {}

    /**
     * Gets a new audio input stream to decode Ogg/Vorbis Audio from
     * an input stream.
     *
     * @param file The <code>File</code> containing the content.
     * @return A new <code>AudioInputStream</code> to decode the input.
     */
    public AudioInputStream getOggStream(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        return new OggVorbisAudioInputStream(new OggStream(fis));
    }
}