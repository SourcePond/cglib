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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.lang.reflect.*;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

public class Ops {
    public static final int SWITCH_STYLE_TRIE = 0;
    public static final int SWITCH_STYLE_HASH = 1;
    private static final Signature FIND_CLASS =
      Signature.parse("Class CGLIB$findClass(String)");

    private Ops() {
    }
    
    public static void load_class_this(Emitter e) {
        load_class_helper(e, e.getClassType());
    }
    
    public static void load_class(Emitter e, Type type) {
        if (Emitter.isPrimitive(type)) {
            if (type == Type.VOID_TYPE) {
                throw new IllegalArgumentException("cannot load void type");
            }
            e.getstatic(getBoxedType(type), "TYPE", Types.CLASS);
        } else {
            load_class_helper(e, type);
        }
    }

    public static void load_method(Emitter e, Method method) {
        load_class(e, Type.getType(method.getDeclaringClass()));
        e.push(method.getName());
        push_object(e, method.getParameterTypes());
        e.invoke_virtual(Types.CLASS, Signatures.GET_DECLARED_METHOD);
    }

    private static void load_class_helper(final Emitter e, Type type) {
        e.register(FIND_CLASS, new Emitter.FinalizeCallback() {
            public void process() {
                generateFindClass(e);
            }
        });
        e.push(emulateClassGetName(type));
        e.invoke_static_this(FIND_CLASS);
    }

    private static String emulateClassGetName(Type type) {
        if (Emitter.isPrimitive(type) || Emitter.isArray(type)) {
            return type.getDescriptor().replace('/', '.');
        } else {
            return type.getClassName();
        }
    }

    private static void generateFindClass(Emitter e) {
        /* generates:
           static private Class findClass(String name) throws Exception {
               try {
                   return Class.forName(name);
               } catch (java.lang.ClassNotFoundException cne) {
                   throw new java.lang.NoClassDefFoundError(cne.getMessage());
               }
           }
         */
        e.begin_method(Constants.PRIVATE_FINAL_STATIC, FIND_CLASS, null);
        Block block = e.begin_block();
        e.load_arg(0);
        e.invoke_static(Types.CLASS, Signatures.FOR_NAME);
        e.return_value();
        e.end_block();
        e.catch_exception(block, Types.CLASS_NOT_FOUND_EXCEPTION);
        e.invoke_virtual(Types.THROWABLE, Signatures.GET_MESSAGE);
        e.new_instance(Types.NO_CLASS_DEF_FOUND_ERROR);
        e.dup_x1();
        e.swap();
        e.invoke_constructor(Types.NO_CLASS_DEF_FOUND_ERROR, Signatures.CSTRUCT_STRING);
        e.athrow();
    }

    /**
     * Allocates and fills an Object[] array with the arguments to the
     * current method. Primitive values are inserted as their boxed
     * (Object) equivalents.
     */
    public static void create_arg_array(Emitter e) {
        /* generates:
           Object[] args = new Object[]{ arg1, new Integer(arg2) };
         */
        Type[] argumentTypes = e.getArgumentTypes();
        e.push(argumentTypes.length);
        e.newarray();
        for (int i = 0; i < argumentTypes.length; i++) {
            e.dup();
            e.push(i);
            e.load_arg(i);
            box(e, argumentTypes[i]);
            e.aastore();
        }
    }
    
    public static void push(Emitter e, Object[] array) {
        e.push(array.length);
        e.newarray(Type.getType(array.getClass().getComponentType()));
        for (int i = 0; i < array.length; i++) {
            e.dup();
            e.push(i);
            push_object(e, array[i]);
            e.aastore();
        }
    }
    
    public static void push_object(Emitter e, Object obj) {
        if (obj == null) {
            e.aconst_null();
        } else {
            Class type = obj.getClass();
            if (type.isArray()) {
                push(e, (Object[])obj);
            } else if (obj instanceof String) {
                e.push((String)obj);
            } else if (obj instanceof Class) {
                load_class(e, Type.getType((Class)obj));
            } else if (obj instanceof BigInteger) {
                e.new_instance(Types.BIG_INTEGER);
                e.dup();
                e.push(obj.toString());
                e.invoke_constructor(Types.BIG_INTEGER);
            } else if (obj instanceof BigDecimal) {
                e.new_instance(Types.BIG_DECIMAL);
                e.dup();
                e.push(obj.toString());
                e.invoke_constructor(Types.BIG_DECIMAL);
            } else if (obj instanceof Number) {
                push_unboxed(e, obj);
            } else {
                throw new IllegalArgumentException("unknown type: " + obj.getClass());
            }
        }
    }

    /**
     * If the object is a Number, Boolean, or Character, pushes the equivalent primitive
     * value onto the stack. Otherwise, calls push_object(obj).
     */
    public static void push_unboxed(Emitter e, Object obj)
    {
        if (obj instanceof Boolean) {
            e.push(((Boolean)obj).booleanValue() ? 1 : 0);
        } else if (obj instanceof Character) {
            e.push((short)((Character)obj).charValue());
        } else if (obj instanceof Long) {
            e.push(((Long)obj).longValue());
        } else if (obj instanceof Double) {
            e.push(((Double)obj).doubleValue());
        } else if (obj instanceof Float) {
            e.push(((Float)obj).floatValue());
        } else if ((obj instanceof Integer) ||
                   (obj instanceof Short) ||
                   (obj instanceof Byte)) {
            e.push(((Number)obj).intValue());
        } else {
            push_object(e, obj);
        }
    }
    
    /**
     * Pushes a zero onto the stack if the argument is a primitive class, or a null otherwise.
     */
    public static void zero_or_null(Emitter e, Type type) {
        if (Emitter.isPrimitive(type)) {
            switch (type.getSort()) {
            case Type.DOUBLE:
                e.push(0d);
                break;
            case Type.LONG:
                e.push(0L);
                break;
            case Type.FLOAT:
                e.push(0f);
                break;
            case Type.VOID:
                e.aconst_null();
            default:
                e.push(0);
            }
        } else {
            e.aconst_null();
        }
    }

    /**
     * Unboxes the object on the top of the stack. If the object is null, the
     * unboxed primitive value becomes zero.
     */
    public static void unbox_or_zero(Emitter e, Type type) {
        if (Emitter.isPrimitive(type)) {
            if (type != Type.VOID_TYPE) {
                Label nonNull = e.make_label();
                Label end = e.make_label();
                e.dup();
                e.ifnonnull(nonNull);
                e.pop();
                zero_or_null(e, type);
                e.goTo(end);
                e.mark(nonNull);
                unbox(e, type);
                e.mark(end);
            }
        } else {
            e.checkcast(type);
        }
    }

    private static Type getBoxedType(Type type) {
        switch (type.getSort()) {
        case Type.CHAR:
            return Types.CHARACTER;
        case Type.BOOLEAN:
            return Types.BOOLEAN;
        case Type.DOUBLE:
            return Types.DOUBLE;
        case Type.FLOAT:
            return Types.FLOAT;
        case Type.LONG:
            return Types.LONG;
        case Type.INT:
            return Types.INTEGER;
        case Type.SHORT:
            return Types.SHORT;
        case Type.BYTE:
            return Types.BYTE;
        default:
            return type;
        }
    }

     /**
      * If the argument is a primitive class, replaces the primitive value
      * on the top of the stack with the wrapped (Object) equivalent. For
      * example, char -> Character.
      * If the class is Void, a null is pushed onto the stack instead.
      * @param type the class indicating the current type of the top stack value
      */
     public static void box(Emitter e, Type type) {
         if (Emitter.isPrimitive(type)) {
             if (type == Type.VOID_TYPE) {
                 e.aconst_null();
             } else {
                 Type boxed = getBoxedType(type);
                 e.new_instance(boxed);
                 if (type.getSize() == 2) {
                     // Pp -> Ppo -> oPpo -> ooPpo -> ooPp -> o
                     e.dup_x2();
                     e.dup_x2();
                     e.pop();
                 } else {
                     // p -> po -> opo -> oop -> o
                     e.dup_x1();
                     e.swap();
                 }
                 e.invoke_constructor(boxed, new Type[]{ type });
             }
         }
     }
    
    /**
     * If the argument is a primitive class, replaces the object
     * on the top of the stack with the unwrapped (primitive)
     * equivalent. For example, Character -> char.
     * @param type the class indicating the desired type of the top stack value
     * @return true if the value was unboxed
     */
    public static void unbox(Emitter e, Type type) {
        Type t = Types.NUMBER;
        Signature sig = null;
        switch (type.getSort()) {
        case Type.VOID:
            return;
        case Type.CHAR:
            t = Types.CHARACTER;
            sig = Signatures.CHAR_VALUE;
            break;
        case Type.BOOLEAN:
            t = Types.BOOLEAN;
            sig = Signatures.BOOLEAN_VALUE;
            break;
        case Type.DOUBLE:
            sig = Signatures.DOUBLE_VALUE;
            break;
        case Type.FLOAT:
            sig = Signatures.FLOAT_VALUE;
            break;
        case Type.LONG:
            sig = Signatures.LONG_VALUE;
            break;
        case Type.INT:
        case Type.SHORT:
        case Type.BYTE:
            sig = Signatures.INT_VALUE;
        }

        if (sig == null) {
            e.checkcast(type);
        } else {
            e.checkcast(t);
            e.invoke_virtual(t, sig);
        }
    }

    /**
     * Process an array on the stack. Assumes the top item on the stack
     * is an array of the specified type. For each element in the array,
     * puts the element on the stack and triggers the callback.
     * @param type the type of the array (type.isArray() must be true)
     * @param callback the callback triggered for each element
     */
    public static void process_array(Emitter e, Type type, ProcessArrayCallback callback) {
        Type componentType = Emitter.getComponentType(type);
        Local array = e.make_local();
        Local loopvar = e.make_local(Type.INT_TYPE);
        Label loopbody = e.make_label();
        Label checkloop = e.make_label();
        e.store_local(array);
        e.push(0);
        e.store_local(loopvar);
        e.goTo(checkloop);
        
        e.mark(loopbody);
        e.load_local(array);
        e.load_local(loopvar);
        e.array_load(componentType);
        callback.processElement(componentType);
        e.iinc(loopvar, 1);
        
        e.mark(checkloop);
        e.load_local(loopvar);
        e.load_local(array);
        e.arraylength();
        e.if_icmplt(loopbody);
    }
    
    /**
     * Process two arrays on the stack in parallel. Assumes the top two items on the stack
     * are arrays of the specified class. The arrays must be the same length. For each pair
     * of elements in the arrays, puts the pair on the stack and triggers the callback.
     * @param type the type of the arrays (type.isArray() must be true)
     * @param callback the callback triggered for each pair of elements
     */
    public static void process_arrays(Emitter e, Type type, ProcessArrayCallback callback) {
        Type componentType = Emitter.getComponentType(type);
        Local array1 = e.make_local();
        Local array2 = e.make_local();
        Local loopvar = e.make_local(Type.INT_TYPE);
        Label loopbody = e.make_label();
        Label checkloop = e.make_label();
        e.store_local(array1);
        e.store_local(array2);
        e.push(0);
        e.store_local(loopvar);
        e.goTo(checkloop);
        
        e.mark(loopbody);
        e.load_local(array1);
        e.load_local(loopvar);
        e.array_load(componentType);
        e.load_local(array2);
        e.load_local(loopvar);
        e.array_load(componentType);
        callback.processElement(componentType);
        e.iinc(loopvar, 1);
        
        e.mark(checkloop);
        e.load_local(loopvar);
        e.load_local(array1);
        e.arraylength();
        e.if_icmplt(loopbody);
    }
    
    /**
     * Branches to the specified label if the top two items on the stack
     * are not equal. The items must both be of the specified
     * class. Equality is determined by comparing primitive values
     * directly and by invoking the <code>equals</code> method for
     * Objects. Arrays are recursively processed in the same manner.
     */
    public static void not_equals(final Emitter e, Type type, final Label notEquals) {
        (new ProcessArrayCallback() {
            public void processElement(Type type) {
                not_equals_helper(e, type, notEquals, this);
            }
        }).processElement(type);
    }
    
    private static void not_equals_helper(Emitter e, Type type, Label notEquals, ProcessArrayCallback callback) {
        if (Emitter.isPrimitive(type)) {
            e.if_cmpne(type, notEquals);
        } else {
            Label end = e.make_label();
            nullcmp(e, notEquals, end);
            if (Emitter.isArray(type)) {
                Label checkContents = e.make_label();
                e.dup2();
                e.arraylength();
                e.swap();
                e.arraylength();
                e.if_icmpeq(checkContents);
                e.pop2();
                e.goTo(notEquals);
                e.mark(checkContents);
                process_arrays(e, type, callback);
            } else {
                e.invoke_virtual(Types.OBJECT, Signatures.EQUALS);
                e.ifeq(notEquals);
            }
            e.mark(end);
        }
    }
    
    ///// TODO: get rid of this
    /**
     * If both objects on the top of the stack are non-null, does nothing.
     * If one is null, or both are null, both are popped off and execution
     * branches to the respective label.
     * @param oneNull label to branch to if only one of the objects is null
     * @param bothNull label to branch to if both of the objects are null
     */
    private static void nullcmp(Emitter e, Label oneNull, Label bothNull) {
        e.dup2();
        Label nonNull = e.make_label();
        Label oneNullHelper = e.make_label();
        Label end = e.make_label();
        e.ifnonnull(nonNull);
        e.ifnonnull(oneNullHelper);
        e.pop2();
        e.goTo(bothNull);
        
        e.mark(nonNull);
        e.ifnull(oneNullHelper);
        e.goTo(end);
        
        e.mark(oneNullHelper);
        e.pop2();
        e.goTo(oneNull);
        
        e.mark(end);
    }
    
    public static void string_switch(Emitter e, String[] strings, int switchStyle, ObjectSwitchCallback callback)
    throws Exception {
        switch (switchStyle) {
        case SWITCH_STYLE_TRIE:
            string_switch_trie(e, strings, callback);
            break;
        case SWITCH_STYLE_HASH:
            string_switch_hash(e, strings, callback);
            break;
        default:
            throw new IllegalArgumentException("unknown switch style " + switchStyle);
        }
    }

    private static void string_switch_trie(final Emitter e,
                                           String[] strings,
                                           final ObjectSwitchCallback callback) throws Exception {
        final Label def = e.make_label();
        final Label end = e.make_label();
        final Map buckets = CollectionUtils.bucket(Arrays.asList(strings), new Transformer() {
            public Object transform(Object value) {
                return new Integer(((String)value).length());
            }
        });
        e.dup();
        e.invoke_virtual(Types.STRING, Signatures.STRING_LENGTH);
        e.process_switch(getSwitchKeys(buckets), new ProcessSwitchCallback() {
                public void processCase(int key, Label ignore_end) throws Exception {
                    List bucket = (List)buckets.get(new Integer(key));
                    stringSwitchHelper(e, bucket, callback, def, end, 0);
                }
                public void processDefault() {
                    e.goTo(def);
                }
            });
        e.mark(def);
        e.pop();
        callback.processDefault();
        e.mark(end);
    }

    private static void stringSwitchHelper(final Emitter e,
                                           List strings,
                                           final ObjectSwitchCallback callback,
                                           final Label def,
                                           final Label end,
                                           final int index) throws Exception {
        final int len = ((String)strings.get(0)).length();
        final Map buckets = CollectionUtils.bucket(strings, new Transformer() {
            public Object transform(Object value) {
                return new Integer(((String)value).charAt(index));
            }
        });
        e.dup();
        e.push(index);
        e.invoke_virtual(Types.STRING, Signatures.STRING_CHAR_AT);
        e.process_switch(getSwitchKeys(buckets), new ProcessSwitchCallback() {
                public void processCase(int key, Label ignore_end) throws Exception {
                    List bucket = (List)buckets.get(new Integer(key));
                    if (index + 1 == len) {
                        e.pop();
                        callback.processCase(bucket.get(0), end);
                    } else {
                        stringSwitchHelper(e, bucket, callback, def, end, index + 1);
                    }
                }
                public void processDefault() {
                    e.goTo(def);
                }
            });
    }        

    private static int[] getSwitchKeys(Map buckets) {
        int[] keys = new int[buckets.size()];
        int index = 0;
        for (Iterator it = buckets.keySet().iterator(); it.hasNext();) {
            keys[index++] = ((Integer)it.next()).intValue();
        }
        Arrays.sort(keys);
        return keys;
    }

    private static void string_switch_hash(final Emitter e,
                                           final String[] strings,
                                           final ObjectSwitchCallback callback) throws Exception {
        final Map buckets = CollectionUtils.bucket(Arrays.asList(strings), new Transformer() {
            public Object transform(Object value) {
                return new Integer(value.hashCode());
            }
        });
        final Label def = e.make_label();
        final Label end = e.make_label();
        e.dup();
        e.invoke_virtual(Types.OBJECT, Signatures.HASH_CODE);
        e.process_switch(getSwitchKeys(buckets), new ProcessSwitchCallback() {
            public void processCase(int key, Label ignore_end) throws Exception {
                List bucket = (List)buckets.get(new Integer(key));
                Label next = null;
                for (Iterator it = bucket.iterator(); it.hasNext();) {
                    String string = (String)it.next();
                    if (next != null) {
                        e.mark(next);
                    }
                    if (it.hasNext()) {
                        e.dup();
                    }
                    e.push(string);
                    e.invoke_virtual(Types.OBJECT, Signatures.EQUALS);
                    if (it.hasNext()) {
                        e.ifeq(next = e.make_label());
                        e.pop();
                    } else {
                        e.ifeq(def);
                    }
                    callback.processCase(string, end);
                }
            }
            public void processDefault() {
                e.pop();
            }
        });
        e.mark(def);
        callback.processDefault();
        e.mark(end);
    }

    private interface ParameterTyper {
        Class[] getParameterTypes(Object member);
    }

    public static void method_switch(Emitter e,
                                     Method[] methods,
                                     ObjectSwitchCallback callback) throws Exception {
        member_switch_helper(e, Arrays.asList(methods), callback, true, new ParameterTyper() {
            public Class[] getParameterTypes(Object member) {
                return ((Method)member).getParameterTypes();
            }
        });
    }

    public static void constructor_switch(Emitter e,
                                          Constructor[] cstructs,
                                          ObjectSwitchCallback callback) throws Exception {
        member_switch_helper(e, Arrays.asList(cstructs), callback, false, new ParameterTyper() {
            public Class[] getParameterTypes(Object member) {
                return ((Constructor)member).getParameterTypes();
            }
        });
    }

    private static void member_switch_helper(final Emitter e,
                                             List members,
                                             final ObjectSwitchCallback callback,
                                             boolean useName,
                                             final ParameterTyper typer) throws Exception {
        final Map cache = new HashMap();
        final ParameterTyper cached = new ParameterTyper() {
            public Class[] getParameterTypes(Object member) {
                Class[] types = (Class[])cache.get(member);
                if (types == null) {
                    cache.put(member, types = typer.getParameterTypes(member));
                }
                return types;
            }
        };
        final Label def = e.make_label();
        final Label end = e.make_label();
        if (useName) {
            e.swap();
            final Map buckets = CollectionUtils.bucket(members, new Transformer() {
                public Object transform(Object value) {
                    return ((Member)value).getName();
                }
            });
            String[] names = (String[])buckets.keySet().toArray(new String[buckets.size()]);
            string_switch_hash(e, names, new ObjectSwitchCallback() {
                public void processCase(Object key, Label dontUseEnd) throws Exception {
                    member_helper_size(e, (List)buckets.get(key), callback, cached, def, end);
                }
                public void processDefault() throws Exception {
                    e.goTo(def);
                }
            });
        } else {
            member_helper_size(e, members, callback, cached, def, end);
        }
        e.mark(def);
        e.pop();
        callback.processDefault();
        e.mark(end);
    }

    private static void member_helper_size(final Emitter e,
                                           List members,
                                           final ObjectSwitchCallback callback,
                                           final ParameterTyper typer,
                                           final Label def,
                                           final Label end) throws Exception {
        final Map buckets = CollectionUtils.bucket(members, new Transformer() {
            public Object transform(Object value) {
                return new Integer(typer.getParameterTypes(value).length);
            }
        });
        e.dup();
        e.arraylength();
        e.process_switch(getSwitchKeys(buckets), new ProcessSwitchCallback() {
            public void processCase(int key, Label dontUseEnd) throws Exception {
                List bucket = (List)buckets.get(new Integer(key));
                Class[] types = typer.getParameterTypes(bucket.get(0));
                member_helper_type(e, bucket, callback, typer, def, end, new BitSet(types.length));
            }
            public void processDefault() throws Exception {
                e.goTo(def);
            }
        });
    }

    private static void member_helper_type(final Emitter e,
                                           List members,
                                           final ObjectSwitchCallback callback,
                                           final ParameterTyper typer,
                                           final Label def,
                                           final Label end,
                                           final BitSet checked) throws Exception {
        if (members.size() == 1) {
            // need to check classes that have not already been checked via switches
            Member member = (Member)members.get(0);
            Class[] types = typer.getParameterTypes(member);
            for (int i = 0; i < types.length; i++) {
                if (checked == null || !checked.get(i)) {
                    e.dup();
                    e.aaload(i);
                    e.invoke_virtual(Types.CLASS, Signatures.GET_NAME);
                    e.push(types[i].getName());
                    e.invoke_virtual(Types.OBJECT, Signatures.EQUALS);
                    e.ifeq(def);
                }
            }
            e.pop();
            callback.processCase(member, end);
        } else {
            // choose the index that has the best chance of uniquely identifying member
            Class[] example = typer.getParameterTypes(members.get(0));
            Map buckets = null;
            int index = -1;
            for (int i = 0; i < example.length; i++) {
                final int j = i;
                Map test = CollectionUtils.bucket(members, new Transformer() {
                    public Object transform(Object value) {
                        return typer.getParameterTypes(value)[j].getName();
                    }
                });
                if (buckets == null || test.size() > buckets.size()) {
                    buckets = test;
                    index = i;
                }
            }
            if (buckets == null) {
                // TODO: switch by returnType
                // must have two methods with same name, types, and different return types
                e.goTo(def);
            } else {
                checked.set(index);

                e.dup();
                e.aaload(index);
                e.invoke_virtual(Types.CLASS, Signatures.GET_NAME);

                final Map fbuckets = buckets;
                String[] names = (String[])buckets.keySet().toArray(new String[buckets.size()]);
                string_switch_hash(e, names, new ObjectSwitchCallback() {
                    public void processCase(Object key, Label dontUseEnd) throws Exception {
                        member_helper_type(e, (List)fbuckets.get(key), callback, typer, def, end, checked);
                    }
                    public void processDefault() throws Exception {
                        e.goTo(def);
                    }
                });
            }
        }
    }

    /////////////// REFLECTEMITTER OPS MOVED HERE, GRADUALLY REMOVE AS POSSIBLE ///////////////////////////

    public static void begin_class(Emitter e,
                                   int access,
                                   String className,
                                   Class superclass,
                                   Class[] interfaces,
                                   String sourceFile) {
        e.begin_class(access,
                      getType(className),
                      (superclass != null) ? Type.getType(superclass) : null,
                      Signature.getTypes(interfaces),
                      sourceFile);
    }

    public static void begin_constructor(Emitter e, Constructor constructor) {
        e.begin_constructor(Constants.ACC_PUBLIC, // constructor.getModifiers(),
                            Signature.getTypes(constructor.getParameterTypes()),
                            Signature.getTypes(constructor.getExceptionTypes()));
    }

    public static void begin_method(Emitter e,
                                    int access,
                                    String name,
                                    Class returnType,
                                    Class[] parameterTypes,
                                    Class[] exceptionTypes) {
        e.begin_method(access,
                       name,
                       Type.getType(returnType),
                       Signature.getTypes(parameterTypes),
                       Signature.getTypes(exceptionTypes));
    }

    public static void begin_method(Emitter e, Method method) {
        begin_method(e, method, getDefaultModifiers(method.getModifiers()));
    }

    public static void begin_method(Emitter e, Method method, int modifiers) {
        e.begin_method(modifiers,
                       new Signature(method),
                       Signature.getTypes(method.getExceptionTypes()));
    }

    public static void getfield(Emitter e,Field field) {
        int opcode = Modifier.isStatic(field.getModifiers()) ? Constants.GETSTATIC : Constants.GETFIELD;
        fieldHelper(e, opcode, field);
    }
    
    public static void putfield(Emitter e, Field field) {
        int opcode = Modifier.isStatic(field.getModifiers()) ? Constants.PUTSTATIC : Constants.PUTFIELD;
        fieldHelper(e, opcode, field);
    }

    private static void fieldHelper(Emitter e, int opcode, Field field) {
        // TODO: remove need for direct access to emit_field?
        e.emit_field(opcode,
                     Type.getType(field.getDeclaringClass()),
                     field.getName(),
                     Type.getType(field.getType()));
    }

    public static void invoke(Emitter e, Method method) {
        int opcode;
        if (method.getDeclaringClass().isInterface()) {
            opcode = Constants.INVOKEINTERFACE;
        } else if (Modifier.isStatic(method.getModifiers())) {
            opcode = Constants.INVOKESTATIC;
        } else {
            opcode = Constants.INVOKEVIRTUAL;
        }
        // TODO: remove need for direct access to emit_invoke?
        e.emit_invoke(opcode,
                      Type.getType(method.getDeclaringClass()),
                      method.getName(),
                      Type.getType(method.getReturnType()),
                      Signature.getTypes(method.getParameterTypes()));
    }

    public static void invoke(Emitter e, Constructor constructor) {
        e.invoke_constructor(Type.getType(constructor.getDeclaringClass()),
                             Signature.getTypes(constructor.getParameterTypes()));
    }

    public static void super_invoke(Emitter e, Method method) {
        // TODO: remove need for direct access to emit_invoke?
        e.emit_invoke(Constants.INVOKESPECIAL,
                      e.getSuperType(),
                      method.getName(),
                      Type.getType(method.getReturnType()),
                      Signature.getTypes(method.getParameterTypes()));
    }

    public static void super_invoke(Emitter e, Constructor constructor) {
        e.super_invoke_constructor(Signature.getTypes(constructor.getParameterTypes()));
    }
    
    public static int getDefaultModifiers(int modifiers) {
        return Constants.ACC_FINAL
            | (modifiers
               & ~Constants.ACC_ABSTRACT
               & ~Constants.ACC_NATIVE
               & ~Constants.ACC_SYNCHRONIZED);
    }
    
    private static Type getType(String fqcn) {
        return Type.getType("L" + fqcn.replace('.', '/') + ";");
    }
}