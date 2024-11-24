package com.github.topi314.lavasrc.deezer;

import com.sedmelluq.discord.lavaplayer.tools.io.ByteBufferInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.http.HttpResponse;

public class DeezerPersistentHttpStream extends PersistentHttpStream {

  private final byte[] keyMaterial;

  public DeezerPersistentHttpStream(
    HttpInterface httpInterface,
    URI contentUrl,
    Long contentLength,
    byte[] keyMaterial
  ) {
    super(httpInterface, contentUrl, contentLength);
    this.keyMaterial = keyMaterial;
  }

  @Override
  public InputStream createContentInputStream(HttpResponse response)
    throws IOException {
    return new DecryptingInputStream(
      response.getEntity().getContent(),
      this.keyMaterial,
      this.position
    );
  }

  private static class DecryptingInputStream extends InputStream {

    private static final int BLOCK_SIZE = 2048;
    private static final byte[] iv = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };

    private final InputStream in;
    private final ByteBuffer buff;
    private final InputStream out;
    private final Cipher cipher;
    private long i;
    private boolean filled;

    public DecryptingInputStream(
      InputStream in,
      byte[] keyMaterial,
      long position
    ) throws IOException {
      this.in = new BufferedInputStream(in);
      this.buff = ByteBuffer.allocate(BLOCK_SIZE);
      this.out = new ByteBufferInputStream(this.buff);

      try {
        cipher = Cipher.getInstance("Blowfish/CBC/NoPadding");
        cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(keyMaterial, "Blowfish"),
          new IvParameterSpec(iv)
        );
      } catch (
        NoSuchAlgorithmException
        | NoSuchPaddingException
        | InvalidKeyException
        | InvalidAlgorithmParameterException e
      ) {
        throw new IOException(e);
      }

      i = Math.max(0, position / BLOCK_SIZE);
      var remainingBytesInChunk = ((i + 1) * BLOCK_SIZE) - position;
      if (remainingBytesInChunk < 2048) {
        in.skip(remainingBytesInChunk);
        i++;
      }
    }

    @Override
    public int read() throws IOException {
      if (this.filled && this.out.available() > 0) {
        return this.out.read();
      }
      var chunk = this.in.readNBytes(BLOCK_SIZE);
      this.buff.clear();
      this.filled = true;
      if (this.i % 3 > 0 || chunk.length < BLOCK_SIZE) {
        this.buff.put(chunk);
      } else {
        byte[] decryptedChunk;
        try {
          decryptedChunk = this.cipher.doFinal(chunk);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
          throw new RuntimeException(e);
        }
        this.buff.put(decryptedChunk);
      }
      i++;
      this.buff.flip();
      return this.out.read();
    }
  }
}
