package org.nio4r;

import org.jruby.*;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/*
created by Upekshej
 */
public class ByteBuffer extends RubyObject {

    private java.nio.ByteBuffer byteBuffer;
    private String currentWritePath = "";
    private String currentReadPath = "";

    private FileChannel currentWriteFileChannel;
    private FileOutputStream fileOutputStream;

    private FileInputStream currentReadChannel;
    private FileChannel inChannel;

    public ByteBuffer(final Ruby ruby, RubyClass rubyClass) {
        super(ruby, rubyClass);
    }

    @JRubyMethod
    public IRubyObject initialize(ThreadContext context, IRubyObject capacity_or_string, IRubyObject offset, IRubyObject length) {
        Ruby ruby = context.getRuntime();

        if (capacity_or_string instanceof RubyString) {
            if (offset != ruby.getNil() && length != ruby.getNil()) {
                int arrayOffset = RubyNumeric.num2int(offset);
                int arrayLimit = RubyNumeric.num2int(length);
                byteBuffer = java.nio.ByteBuffer.wrap(capacity_or_string.asJavaString().getBytes(), arrayOffset, arrayLimit);
            } else {
                byteBuffer = java.nio.ByteBuffer.wrap(capacity_or_string.asJavaString().getBytes());
            }
        } else if (capacity_or_string instanceof RubyInteger) {
            int allocationSize = RubyNumeric.num2int(capacity_or_string);
            byteBuffer = java.nio.ByteBuffer.allocate(allocationSize);
        } else {
            throw ruby.newTypeError("expected Integer or String argument, got " + capacity_or_string.getType().toString());
        }

        return this;
    }

    /**
     * Currently assuming only strings will come..
     *
     * @param context
     * @return
     */
    @JRubyMethod(name = "<<")
    public IRubyObject put(ThreadContext context, IRubyObject str) {
        String string = str.asJavaString();

        if (byteBuffer == null) {
            byteBuffer = java.nio.ByteBuffer.wrap(string.getBytes());
        }

        byteBuffer.put(string.getBytes());
        return this;
    }

    //https://www.ruby-forum.com/topic/3731325
    @JRubyMethod(name = "get")
    public IRubyObject get(ThreadContext context) {
        ArrayList<Byte> temp = new ArrayList<Byte>();

        while (byteBuffer.hasRemaining()) {
            temp.add(byteBuffer.get());
        }

        return JavaUtil.convertJavaToRuby(context.getRuntime(), new String(toPrimitives(temp)));
    }

    @JRubyMethod(name = "read_next")
    public IRubyObject readNext(ThreadContext context, IRubyObject count) {
        int c = RubyNumeric.num2int(count);

        if (c < 1) {
            throw new IllegalArgumentException();
        }

        if (c <= byteBuffer.remaining()) {
            org.jruby.util.ByteList temp = new org.jruby.util.ByteList(c);

            while (c > 0) {
                temp.append(byteBuffer.get());
                c = c - 1;
            }

            return context.runtime.newString(temp);
        }

        return RubyString.newEmptyString(context.runtime);
    }

    private byte[] toPrimitives(ArrayList<Byte> oBytes) {
        byte[] bytes = new byte[oBytes.size()];

        for (int i = 0; i < oBytes.size(); i++) {
            bytes[i] = (oBytes.get(i) == null) ? " ".getBytes()[0] : oBytes.get(i);
        }

        return bytes;
    }

    @JRubyMethod(name = "write_to")
    public IRubyObject writeTo(ThreadContext context, IRubyObject f) {
        try {
            File file = (File) JavaUtil.unwrapJavaObject(f);

            if (!isTheSameFile(file, false)) {
                currentWritePath = file.getAbsolutePath();
                if (currentWriteFileChannel != null) currentWriteFileChannel.close();
                if (fileOutputStream != null) fileOutputStream.close();

                fileOutputStream = new FileOutputStream(file, true);
                currentWriteFileChannel = fileOutputStream.getChannel();
            }

            currentWriteFileChannel.write(byteBuffer);
        } catch (Exception e) {
            throw new IllegalArgumentException("write error: " + e.getLocalizedMessage());
        }

        return this;
    }

    @JRubyMethod(name = "read_from")
    public IRubyObject readFrom(ThreadContext context, IRubyObject f) {
        try {
            File file = (File) JavaUtil.unwrapJavaObject(f);

            if (!isTheSameFile(file, true)) {
                inChannel.close();
                currentReadChannel.close();
                currentReadPath = file.getAbsolutePath();
                currentReadChannel = new FileInputStream(file);
                inChannel = currentReadChannel.getChannel();
            }

            inChannel.read(byteBuffer);
        } catch (Exception e) {
            throw new IllegalArgumentException("read error: " + e.getLocalizedMessage());
        }

        return this;
    }

    private boolean isTheSameFile(File f, boolean read) {
        if (read) {
            return (currentReadPath == f.getAbsolutePath());
        }

        return currentWritePath == f.getAbsolutePath();
    }

    @JRubyMethod(name = "remaining")
    public IRubyObject remainingPositions(ThreadContext context) {
        int count = byteBuffer.remaining();
        return context.getRuntime().newFixnum(count);
    }

    @JRubyMethod(name = "remaining?")
    public IRubyObject hasRemaining(ThreadContext context) {
        if (byteBuffer.hasRemaining()) {
            return context.getRuntime().getTrue();
        }

        return context.getRuntime().getFalse();
    }

    @JRubyMethod(name = "offset?")
    public IRubyObject getOffset(ThreadContext context) {
        int offset = byteBuffer.arrayOffset();
        return context.getRuntime().newFixnum(offset);
    }

    /**
     * Check whether the two ByteBuffers are the same.
     *
     * @param context
     * @param ob      : The RubyObject which needs to be check
     * @return
     */
    @JRubyMethod(name = "equals?")
    public IRubyObject equals(ThreadContext context, IRubyObject obj) {
        Object o = JavaUtil.convertRubyToJava(obj);

        if(!(o instanceof ByteBuffer)) {
            return context.getRuntime().getFalse();
        }

        if(this.byteBuffer.equals(((ByteBuffer)o).getBuffer())) {
            return context.getRuntime().getTrue();
        } else {
            return context.getRuntime().getFalse();
        }
    }

    /**
     * Flip capability provided by the java nio.ByteBuffer
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel
     *
     * @param context
     * @return
     */
    @JRubyMethod
    public IRubyObject flip(ThreadContext context) {
        byteBuffer.flip();
        return this;
    }

    /**
     * Rewinds the buffer. Usage in java is like
     * out.write(buf);    // Write remaining data
     * buf.rewind();      // Rewind buffer
     * buf.get(array);    // Copy data into array
     *
     * @param context
     * @return
     */
    @JRubyMethod
    public IRubyObject rewind(ThreadContext context) {
        byteBuffer.rewind();
        return this;
    }

    @JRubyMethod
    public IRubyObject reset(ThreadContext context) {
        byteBuffer.reset();
        return this;
    }

    @JRubyMethod
    public IRubyObject mark(ThreadContext context) {
        byteBuffer.mark();
        return this;
    }

    /**
     * Removes all the content in the byteBuffer
     *
     * @param context
     * @return
     */
    @JRubyMethod
    public IRubyObject clear(ThreadContext context) {
        byteBuffer.clear();
        return this;
    }

    @JRubyMethod
    public IRubyObject compact(ThreadContext context) {
        byteBuffer.compact();
        return this;
    }

    @JRubyMethod(name = "capacity")
    public IRubyObject capacity(ThreadContext context) {
        int cap = byteBuffer.capacity();
        return context.getRuntime().newFixnum(cap);
    }

    @JRubyMethod
    public IRubyObject position(ThreadContext context, IRubyObject newPosition) {
        int position = RubyNumeric.num2int(newPosition);
        byteBuffer.position(position);
        return this;
    }

    @JRubyMethod(name = "limit")
    public IRubyObject limit(ThreadContext context, IRubyObject newLimit) {
        int limit = RubyNumeric.num2int(newLimit);
        byteBuffer.limit(limit);
        return this;
    }

    @JRubyMethod(name = "limit?")
    public IRubyObject limit(ThreadContext context) {
        int lmt = byteBuffer.limit();
        return context.getRuntime().newFixnum(lmt);
    }

    @JRubyMethod(name = "to_s")
    public IRubyObject to_String(ThreadContext context) {
        return JavaUtil.convertJavaToRuby(context.getRuntime(), byteBuffer.toString());
    }

    public java.nio.ByteBuffer getBuffer() {
        return byteBuffer;
    }
}
