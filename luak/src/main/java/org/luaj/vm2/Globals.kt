/*******************************************************************************
 * Copyright (c) 2012 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.luaj.vm2

import java.io.IOException
import java.io.InputStream
import java.io.PrintStream
import java.io.Reader

import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.IoLib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.ResourceFinder

/**
 * Global environment used by luaj.  Contains global variables referenced by executing lua.
 *
 *
 *
 * <h3>Constructing and Initializing Instances</h3>
 * Typically, this is constructed indirectly by a call to
 * [org.luaj.vm2.lib.jse.JsePlatform.standardGlobals] or
 * [org.luaj.vm2.lib.jme.JmePlatform.standardGlobals],
 * and then used to load lua scripts for execution as in the following example.
 * <pre> `Globals globals = JsePlatform.standardGlobals();
 * globals.load( new StringReader("print 'hello'"), "main.lua" ).call();
` *  </pre>
 * The creates a complete global environment with the standard libraries loaded.
 *
 *
 * For specialized circumstances, the Globals may be constructed directly and loaded
 * with only those libraries that are needed, for example.
 * <pre> `Globals globals = new Globals();
 * globals.load( new BaseLib() );
` *  </pre>
 *
 * <h3>Loading and Executing Lua Code</h3>
 * Globals contains convenience functions to load and execute lua source code given a Reader.
 * A simple example is:
 * <pre> `globals.load( new StringReader("print 'hello'"), "main.lua" ).call();
` *  </pre>
 *
 * <h3>Fine-Grained Control of Compiling and Loading Lua</h3>
 * Executable LuaFunctions are created from lua code in several steps
 *
 *  * find the resource using the platform's [ResourceFinder]
 *  * compile lua to lua bytecode using [Compiler]
 *  * load lua bytecode to a [Prototype] using [Undumper]
 *  * construct [LuaClosure] from [Prototype] with [Globals] using [Loader]
 *
 *
 *
 * There are alternate flows when the direct lua-to-Java bytecode compiling [org.luaj.vm2.luajc.LuaJC] is used.
 *
 *  * compile lua to lua bytecode using [Compiler] or load precompiled code using [Undumper]
 *  * convert lua bytecode to equivalent Java bytecode using [org.luaj.vm2.luajc.LuaJC] that implements [Loader] directly
 *
 *
 * <h3>Java Field</h3>
 * Certain public fields are provided that contain the current values of important global state:
 *
 *  * [.STDIN] Current value for standard input in the laaded [IoLib], if any.
 *  * [.STDOUT] Current value for standard output in the loaded [IoLib], if any.
 *  * [.STDERR] Current value for standard error in the loaded [IoLib], if any.
 *  * [.finder] Current loaded [ResourceFinder], if any.
 *  * [.compiler] Current loaded [Compiler], if any.
 *  * [.undumper] Current loaded [Undumper], if any.
 *  * [.loader] Current loaded [Loader], if any.
 *
 *
 * <h3>Lua Environment Variables</h3>
 * When using [org.luaj.vm2.lib.jse.JsePlatform] or [org.luaj.vm2.lib.jme.JmePlatform],
 * these environment variables are created within the Globals.
 *
 *  * "_G" Pointer to this Globals.
 *  * "_VERSION" String containing the version of luaj.
 *
 *
 * <h3>Use in Multithreaded Environments</h3>
 * In a multi-threaded server environment, each server thread should create one Globals instance,
 * which will be logically distinct and not interfere with each other, but share certain
 * static immutable resources such as class data and string data.
 *
 *
 *
 * @see org.luaj.vm2.lib.jse.JsePlatform
 *
 * @see org.luaj.vm2.lib.jme.JmePlatform
 *
 * @see LuaValue
 *
 * @see Compiler
 *
 * @see Loader
 *
 * @see Undumper
 *
 * @see ResourceFinder
 *
 * @see org.luaj.vm2.compiler.LuaC
 *
 * @see org.luaj.vm2.luajc.LuaJC
 */
open class Globals : LuaTable() {

    /** The current default input stream.  */
    @JvmField var STDIN = System.`in`

    /** The current default output stream.  */
    @JvmField var STDOUT = System.out

    /** The current default error stream.  */
    @JvmField var STDERR = System.err

    /** The installed ResourceFinder for looking files by name.  */
    @JvmField var finder: ResourceFinder? = null

    /** The currently running thread.  Should not be changed by non-library code.  */
    @JvmField var running: LuaThread = LuaThread(this)

    /** The BaseLib instance loaded into this Globals  */
    @JvmField var baselib: BaseLib? = null

    /** The PackageLib instance loaded into this Globals  */
    @JvmField var package_: PackageLib? = null

    /** The DebugLib instance loaded into this Globals, or null if debugging is not enabled  */
    @JvmField var debuglib: DebugLib? = null

    /** The installed loader.
     * @see Loader
     */
    @JvmField var loader: Loader? = null

    /** The installed compiler.
     * @see Compiler
     */
    @JvmField var compiler: Compiler? = null

    /** The installed undumper.
     * @see Undumper
     */
    @JvmField var undumper: Undumper? = null

    /** Interface for module that converts a Prototype into a LuaFunction with an environment.  */
    interface Loader {
        /** Convert the prototype into a LuaFunction with the supplied environment.  */
        @Throws(IOException::class)
        fun load(prototype: Prototype, chunkname: String, env: LuaValue): LuaFunction
    }

    /** Interface for module that converts lua source text into a prototype.  */
    interface Compiler {
        /** Compile lua source into a Prototype. The InputStream is assumed to be in UTF-8.  */
        @Throws(IOException::class)
        fun compile(stream: InputStream, chunkname: String): Prototype
    }

    /** Interface for module that loads lua binary chunk into a prototype.  */
    interface Undumper {
        /** Load the supplied input stream into a prototype.  */
        @Throws(IOException::class)
        fun undump(stream: InputStream, chunkname: String): Prototype?
    }

    /** Check that this object is a Globals object, and return it, otherwise throw an error.  */
    override fun checkglobals(): Globals {
        return this
    }

    /** Convenience function for loading a file that is either binary lua or lua source.
     * @param filename Name of the file to load.
     * @return LuaValue that can be call()'ed or invoke()'ed.
     * @throws LuaError if the file could not be loaded.
     */
    fun loadfile(filename: String): LuaValue {
        try {
            return load(finder!!.findResource(filename)!!, "@$filename", "bt", this)
        } catch (e: Exception) {
            return LuaValue.error("load $filename: $e")
        }

    }

    /** Convenience function to load a string value as a script.  Must be lua source.
     * @param script Contents of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    open fun load(script: String, chunkname: String): LuaValue {
        return load(StrReader(script), chunkname)
    }

    /** Convenience function to load a string value as a script.  Must be lua source.
     * @param script Contents of a lua script, such as "print 'hello, world.'"
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    open fun load(script: String): LuaValue {
        return load(StrReader(script), script)
    }

    /** Convenience function to load a string value as a script with a custom environment.
     * Must be lua source.
     * @param script Contents of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param environment LuaTable to be used as the environment for the loaded function.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(script: String, chunkname: String, environment: LuaTable): LuaValue {
        return load(StrReader(script), chunkname, environment)
    }

    /** Load the content form a reader as a text file.  Must be lua source.
     * The source is converted to UTF-8, so any characters appearing in quoted literals
     * above the range 128 will be converted into multiple bytes.
     * @param reader Reader containing text of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(reader: Reader, chunkname: String): LuaValue {
        return load(UTF8Stream(reader), chunkname, "t", this)
    }

    /** Load the content form a reader as a text file, supplying a custom environment.
     * Must be lua source. The source is converted to UTF-8, so any characters
     * appearing in quoted literals above the range 128 will be converted into
     * multiple bytes.
     * @param reader Reader containing text of a lua script, such as "print 'hello, world.'"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param environment LuaTable to be used as the environment for the loaded function.
     * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
     * @throws LuaError if the script could not be compiled.
     */
    fun load(reader: Reader, chunkname: String, environment: LuaTable): LuaValue {
        return load(UTF8Stream(reader), chunkname, "t", environment)
    }

    /** Load the content form an input stream as a binary chunk or text file.
     * @param is InputStream containing a lua script or compiled lua"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param mode String containing 'b' or 't' or both to control loading as binary or text or either.
     * @param environment LuaTable to be used as the environment for the loaded function.
     */
    fun load(`is`: InputStream, chunkname: String, mode: String, environment: LuaValue): LuaValue {
        try {
            val p = loadPrototype(`is`, chunkname, mode)
            return loader!!.load(p, chunkname, environment)
        } catch (l: LuaError) {
            throw l
        } catch (e: Exception) {
            return LuaValue.error("load $chunkname: $e")
        }
    }

    /** Load lua source or lua binary from an input stream into a Prototype.
     * The InputStream is either a binary lua chunk starting with the lua binary chunk signature,
     * or a text input file.  If it is a text input file, it is interpreted as a UTF-8 byte sequence.
     * @param is Input stream containing a lua script or compiled lua"
     * @param chunkname Name that will be used within the chunk as the source.
     * @param mode String containing 'b' or 't' or both to control loading as binary or text or either.
     */
    @Throws(IOException::class)
    fun loadPrototype(`is`: InputStream, chunkname: String, mode: String): Prototype {
        var `is` = `is`
        if (mode.indexOf('b') >= 0) {
            if (undumper == null)
                LuaValue.error("No undumper.")
            if (!`is`.markSupported())
                `is` = BufferedStream(`is`)
            `is`.mark(4)
            val p = undumper!!.undump(`is`, chunkname)
            if (p != null)
                return p
            `is`.reset()
        }
        if (mode.indexOf('t') >= 0) {
            return compilePrototype(`is`, chunkname)
        }
        LuaValue.error("Failed to load prototype $chunkname using mode '$mode'")
        //return null
        kotlin.error("Failed to load prototype $chunkname using mode '$mode'")
    }

    /** Compile lua source from a Reader into a Prototype. The characters in the reader
     * are converted to bytes using the UTF-8 encoding, so a string literal containing
     * characters with codepoints 128 or above will be converted into multiple bytes.
     */
    @Throws(IOException::class)
    fun compilePrototype(reader: Reader, chunkname: String): Prototype {
        return compilePrototype(UTF8Stream(reader), chunkname)
    }

    /** Compile lua source from an InputStream into a Prototype.
     * The input is assumed to be UTf-8, but since bytes in the range 128-255 are passed along as
     * literal bytes, any ASCII-compatible encoding such as ISO 8859-1 may also be used.
     */
    @Throws(IOException::class)
    fun compilePrototype(stream: InputStream, chunkname: String): Prototype {
        if (compiler == null)
            LuaValue.error("No compiler.")
        return compiler!!.compile(stream, chunkname)
    }

    /** Function which yields the current thread.
     * @param args  Arguments to supply as return values in the resume function of the resuming thread.
     * @return Values supplied as arguments to the resume() call that reactivates this thread.
     */
    fun yield(args: Varargs): Varargs {
        if (running == null || running!!.isMainThread)
            throw LuaError("cannot yield main thread")
        val s = running!!.state
        return s.lua_yield(args)
    }

    /** Reader implementation to read chars from a String in JME or JSE.  */
    internal class StrReader(val s: String) : Reader() {
        var i = 0
        val n: Int

        init {
            n = s.length
        }

        @Throws(IOException::class)
        override fun close() {
            i = n
        }

        @Throws(IOException::class)
        override fun read(): Int {
            return if (i < n) s[i++].toInt() and 0xFF else -1
        }

        @Throws(IOException::class)
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            var j = 0
            while (j < len && i < n) {
                cbuf[off + j] = s[i]
                ++j
                ++i
            }
            return if (j > 0 || len == 0) j else -1
        }
    }

    /* Abstract base class to provide basic buffered input storage and delivery.
	 * This class may be moved to its own package in the future.
	 */
    internal abstract class AbstractBufferedStream protected constructor(buflen: Int) : InputStream() {
        protected var b: ByteArray
        protected var i = 0
        protected var j = 0

        init {
            this.b = ByteArray(buflen)
        }

        @Throws(IOException::class)
        protected abstract fun avail(): Int

        @Throws(IOException::class)
        override fun read(): Int {
            val a = avail()
            return if (a <= 0) -1 else 0xff and b[i++].toInt() and 0xFF
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray): Int {
            return read(b, 0, b.size)
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, i0: Int, n: Int): Int {
            val a = avail()
            if (a <= 0) return -1
            val n_read = Math.min(a, n)
            System.arraycopy(this.b, i, b, i0, n_read)
            i += n_read
            return n_read
        }

        @Throws(IOException::class)
        override fun skip(n: Long): Long {
            val k = Math.min(n, (j - i).toLong())
            i += k.toInt()
            return k
        }

        @Throws(IOException::class)
        override fun available(): Int {
            return j - i
        }
    }

    /**  Simple converter from Reader to InputStream using UTF8 encoding that will work
     * on both JME and JSE.
     * This class may be moved to its own package in the future.
     */
    internal class UTF8Stream(private val r: Reader) : AbstractBufferedStream(96) {
        private val c = CharArray(32)
        @Throws(IOException::class)
        override fun avail(): Int {
            if (i < j) return j - i
            var n = r.read(c)
            if (n < 0)
                return -1
            if (n == 0) {
                val u = r.read()
                if (u < 0)
                    return -1
                c[0] = u.toChar()
                n = 1
            }
            j = LuaString.encodeToUtf8(c, n, b, run { i = 0; i })
            return j
        }

        @Throws(IOException::class)
        override fun close() {
            r.close()
        }
    }

    /** Simple buffered InputStream that supports mark.
     * Used to examine an InputStream for a 4-byte binary lua signature,
     * and fall back to text input when the signature is not found,
     * as well as speed up normal compilation and reading of lua scripts.
     * This class may be moved to its own package in the future.
     */
    internal class BufferedStream(buflen: Int, private val s: InputStream) : AbstractBufferedStream(buflen) {
        constructor(s: InputStream) : this(128, s) {}

        @Throws(IOException::class)
        override fun avail(): Int {
            if (i < j) return j - i
            if (j >= b.size) {
                j = 0
                i = j
            }
            // leave previous bytes in place to implement mark()/reset().
            var n = s.read(b, j, b.size - j)
            if (n < 0)
                return -1
            if (n == 0) {
                val u = s.read()
                if (u < 0)
                    return -1
                b[j] = u.toByte()
                n = 1
            }
            j += n
            return n
        }

        @Throws(IOException::class)
        override fun close() {
            s.close()
        }

        @Synchronized
        override fun mark(n: Int) {
            if (i > 0 || n > b.size) {
                val dest = if (n > b.size) ByteArray(n) else b
                System.arraycopy(b, i, dest, 0, j - i)
                j -= i
                i = 0
                b = dest
            }
        }

        override fun markSupported(): Boolean {
            return true
        }

        @Synchronized
        @Throws(IOException::class)
        override fun reset() {
            i = 0
        }
    }
}
