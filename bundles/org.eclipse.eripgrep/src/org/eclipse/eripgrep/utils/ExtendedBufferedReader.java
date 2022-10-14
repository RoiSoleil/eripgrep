package org.eclipse.eripgrep.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public final class ExtendedBufferedReader extends BufferedReader {

  static final char CR = '\r';

  static final String CRLF = "\r\n";

  static final int END_OF_STREAM = -1;

  static final char LF = '\n';

  static final int UNDEFINED = -2;

  private int lastChar = UNDEFINED;

  private long eolCounter;

  private long position;

  private boolean closed;

  public ExtendedBufferedReader(final Reader reader) {
    super(reader);
  }

  @Override
  public void close() throws IOException {
    closed = true;
    lastChar = END_OF_STREAM;
    super.close();
  }

  public long getCurrentLineNumber() {
    if (lastChar == CR || lastChar == LF || lastChar == UNDEFINED || lastChar == END_OF_STREAM) {
      return eolCounter;
    }
    return eolCounter + 1;
  }

  public int getLastChar() {
    return lastChar;
  }

  public long getPosition() {
    return this.position;
  }

  public boolean isClosed() {
    return closed;
  }

  public int lookAhead() throws IOException {
    super.mark(1);
    final int c = super.read();
    super.reset();
    return c;
  }

  public char[] lookAhead(final char[] buf) throws IOException {
    final int n = buf.length;
    super.mark(n);
    super.read(buf, 0, n);
    super.reset();

    return buf;
  }

  public char[] lookAhead(final int n) throws IOException {
    final char[] buf = new char[n];
    return lookAhead(buf);
  }

  @Override
  public int read() throws IOException {
    final int current = super.read();
    if (current == CR || current == LF && lastChar != CR
        || current == END_OF_STREAM && lastChar != CR && lastChar != LF && lastChar != END_OF_STREAM) {
      eolCounter++;
    }
    lastChar = current;
    position++;
    return lastChar;
  }

  @Override
  public int read(final char[] buf, final int offset, final int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    final int len = super.read(buf, offset, length);
    if (len > 0) {
      for (int i = offset; i < offset + len; i++) {
        final char ch = buf[i];
        if (ch == LF) {
          if (CR != (i > offset ? buf[i - 1] : lastChar)) {
            eolCounter++;
          }
        } else if (ch == CR) {
          eolCounter++;
        }
      }
      lastChar = buf[offset + len - 1];
    } else if (len == -1) {
      lastChar = END_OF_STREAM;
    }
    position += len;
    return len;
  }

  @Override
  public String readLine() throws IOException {
    if (lookAhead() == END_OF_STREAM) {
      return null;
    }
    final StringBuilder buffer = new StringBuilder();
    while (true) {
      final int current = read();
      if (current == CR) {
        final int next = lookAhead();
        if (next == LF) {
          read();
        }
      }
      if (current == END_OF_STREAM || current == LF || current == CR) {
        break;
      }
      buffer.append((char) current);
    }
    return buffer.toString();
  }

}