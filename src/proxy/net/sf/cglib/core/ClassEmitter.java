/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package net.sf.cglib.core;

import java.io.*;
import java.util.*;
import org.objectweb.asm.*;

/**
 * @author Juozas Baliuka, Chris Nokleberg
 */
public class ClassEmitter extends ClassAdapter {
    private static final Signature STATIC_HOOK =
      TypeUtils.parseSignature("void CGLIB$STATIC_HOOK()");

    private int access;
    private Type classType;
    private Type superType;
    private Map fieldInfo;
    private boolean seenStatic;
    private ClassEmitter rec;
    private ClassVisitor raw;
    private CodeEmitter hook;
    private boolean useHook = true;

    public ClassEmitter(ClassVisitor cv) {
        super(null);
        setTarget(cv);
    }

    public ClassEmitter(ClassVisitor cv, boolean useHook) {
        super(null);
        this.useHook = useHook;
        setTarget(cv);
    }

    public ClassEmitter() {
        super(null);
    }

    public void setTarget(ClassVisitor cv) {
        if (useHook) {
            this.raw = cv;
            this.cv = new ClassVisitorTee(rec = new ClassEmitter(new ClassRecorder(), false), cv);
        } else {
            this.raw = cv;
            this.cv = cv;
        }
        fieldInfo = new HashMap();
        seenStatic = false;
        hook = null;
    }

    public void begin_class(int access, String className, Type superType, Type[] interfaces, String sourceFile) {
        this.access = access;
        this.classType = Type.getType("L" + className.replace('.', '/') + ";");
        this.superType = (superType != null) ? superType : Constants.TYPE_OBJECT;
        cv.visit(access,
                 this.classType.getInternalName(),
                 this.superType.getInternalName(),
                 TypeUtils.toInternalNames(interfaces),
                 sourceFile);
        init();
    }

    public CodeEmitter getStaticHook() {
        if (!useHook || TypeUtils.isInterface(access)) {
            throw new IllegalStateException("static hook is invalid for this class");
        }
        if (hook == null) { 
            hook = rec.begin_method(Constants.ACC_STATIC, STATIC_HOOK, null);
            hook.setStaticHook(true);
        }
        return hook;
    }

    protected void init() {
    }

    public int getAccess() {
        return access;
    }

    public Type getClassType() {
        return classType;
    }

    public Type getSuperType() {
        return superType;
    }

    public void end_class() {
        if (useHook && !TypeUtils.isInterface(access)) {
            if (hook != null && !seenStatic) {
                CodeEmitter e = begin_static();
                e.return_value();
                e.end_method();
            }
            if (seenStatic) {
                getStaticHook(); // make sure hook is not null
                hook.return_value();
                hook.end_method();
                ((ClassRecorder)rec.cv).generateMethod(STATIC_HOOK, raw);
            }
        }
        raw.visitEnd();
        cv = raw = null; // for safety
    }

    public CodeEmitter begin_method(int access, Signature sig, Type[] exceptions) {
        CodeVisitor v = cv.visitMethod(access,
                                       sig.getName(),
                                       sig.getDescriptor(),
                                       TypeUtils.toInternalNames(exceptions));
        CodeEmitter e = new CodeEmitter(this, v, access, sig, exceptions);
        if (useHook && sig.equals(Constants.SIG_STATIC) && !TypeUtils.isInterface(access)) {
            seenStatic = true;
            e.invoke_static_this(STATIC_HOOK);
        }
        return e;
    }

    public CodeEmitter begin_static() {
        return begin_method(Constants.ACC_STATIC, Constants.SIG_STATIC, null);
    }

    public void declare_field(int access, String name, Type type, Object value) {
        FieldInfo existing = (FieldInfo)fieldInfo.get(name);
        FieldInfo info = new FieldInfo(access, name, type, value);
        if (existing != null) {
            if (!info.equals(existing)) {
                throw new IllegalArgumentException("Field \"" + name + "\" has been declared differently");
            }
        } else {
            fieldInfo.put(name, info);
            cv.visitField(access, name, type.getDescriptor(), value);
        }
    }

    // TODO: make public?
    boolean isFieldDeclared(String name) {
        return fieldInfo.get(name) != null;
    }

    FieldInfo getFieldInfo(String name) {
        FieldInfo field = (FieldInfo)fieldInfo.get(name);
        if (field == null) {
            throw new IllegalArgumentException("Field " + name + " is not declared in " + classType.getClassName());
        }
        return field;
    }
    
    static class FieldInfo {
        int access;
        String name;
        Type type;
        Object value;
        
        public FieldInfo(int access, String name, Type type, Object value) {
            this.access = access;
            this.name = name;
            this.type = type;
            this.value = value;
        }

        public boolean equals(Object o) {
            if (o == null)
                return false;
            if (!(o instanceof FieldInfo))
                return false;
            FieldInfo other = (FieldInfo)o;
            if (access != other.access ||
                !name.equals(other.name) ||
                !type.equals(other.type)) {
                return false;
            }
            if ((value == null) ^ (other.value == null))
                return false;
            if (value != null && !value.equals(other.value))
                return false;
            return true;
        }

        public int hashCode() {
            return access ^ name.hashCode() ^ type.hashCode() ^ ((value == null) ? 0 : value.hashCode());
        }
    }

    public void visit(int access, String name, String superName, String[] interfaces, String sourceFile) {
        begin_class(access,
                    name.replace('/', '.'),
                    TypeUtils.fromInternalName(superName),
                    TypeUtils.fromInternalNames(interfaces),
                    sourceFile);
    }
    
    public void visitEnd() {
        end_class();
    }
    
    public void visitField(int access, String name, String desc, Object value) {
        declare_field(access, name, Type.getType(desc), value);
    }

    // TODO: handle visitInnerClass?
    
    public CodeVisitor visitMethod(int access, String name, String desc, String[] exceptions) {
        return begin_method(access,
                            new Signature(name, desc),
                            TypeUtils.fromInternalNames(exceptions));
    }
}