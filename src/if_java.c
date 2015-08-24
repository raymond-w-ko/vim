/* vi:set ts=8 sts=4 sw=4 noet:
 *
 * VIM - Vi IMproved	by Bram Moolenaar
 *
 * Java interface by Raymond W. Ko
 *
 * Do ":help uganda"  in Vim to read copying and usage conditions.
 * Do ":help credits" in Vim to see a list of people who contributed.
 * See README.txt for an overview of the Vim source code.
 */

#include "vim.h"

/* all mangled method signatures from "javap -s -p Vim" */

/* Only do the following when the feature is enabled.  Needed for "make
 * depend". */
#if defined(FEAT_JAVA) || defined(PROTO)

#include <jni.h>

static const char JVM_CLASSPATH_OPTION_PREFIX[] = "-Djava.class.path=";
static const char JVM_VIM_CLASS_NAME[] = "vim/Vim";
static const char JVM_VIM_LIST_CLASS_NAME[] = "vim/List";
static const char JVM_VIM_DICT_CLASS_NAME[] = "vim/Dict";
static const char JVM_VIM_DICT_ITERATOR_CLASS_NAME[] = "vim/Dict$DictIterator";
static const char JVM_VIM_BUFFER_CLASS_NAME[] = "vim/Buffer";
static const char JVM_VIM_WINDOW_CLASS_NAME[] = "vim/Window";

/* macros to automatically do reference counting for native method that aren't
 * initiated by on the Java side, such as ex_java, ex_javafile, and etc.
 */
#define javaV_PushLocalFrame(CAPACITY) \
    if ((*env)->PushLocalFrame(env, CAPACITY) != 0)	\
    {						\
	EMSG("javaV_PushLocalFrame failed");	\
	return;					\
    }

#define javaV_PopLocalFrame \
    (*env)->PopLocalFrame(env, NULL);

#define javaV_ThrowAndReturn(ENV, MESSAGE) \
    do											\
    {											\
	(*env)->ThrowNew(ENV, (*ENV)->FindClass(ENV, "java/lang/Exception"), MESSAGE);	\
	return;										\
    }											\
    while (0);

static JavaVM *jvm = NULL;
static JNIEnv *env = NULL;

#ifdef DYNAMIC_JAVA

#ifndef WIN3264
 #include <dlfcn.h>
 #define HANDLE void*
 #define load_dll(n) dlopen((n), RTLD_LAZY|RTLD_GLOBAL)
 #define symbol_from_dll dlsym
 #define close_dll dlclose
#else
 #define load_dll vimLoadLib
 #define symbol_from_dll GetProcAddress
 #define close_dll FreeLibrary
#endif

/* macros */
#define JNI_CreateJavaVM dll_JNI_CreateJavaVM

/* function declaration */
int (*dll_JNI_CreateJavaVM) (JavaVM **pvm, void **penv, void *args);

/* function table */
typedef void **javaV_function;
typedef struct {
    const char *name;
    javaV_function func;
} javaV_Reg;

static const javaV_Reg javaV_dll[] = {
    {"JNI_CreateJavaVM", (javaV_function) &dll_JNI_CreateJavaVM},
    {NULL, NULL}
};

static HANDLE hinstJava = NULL;

    static void
end_dynamic_java(void)
{
    if (hinstJava)
    {
	close_dll(hinstJava);
	hinstJava = 0;
    }
}

    static int
java_link_init(char *libname, int verbose)
{
    const javaV_Reg *reg;

    if (hinstJava)
	return OK;
    hinstJava = load_dll(libname);
    if (!hinstJava)
    {
	if (verbose)
	    EMSG2(_(e_loadlib), libname);
	return FAIL;
    }
    for (reg = javaV_dll; reg->func; ++reg)
    {
	if ((*reg->func = symbol_from_dll(hinstJava, reg->name)) == NULL)
	{
	    close_dll(hinstJava);
	    hinstJava = 0;
	    if (verbose)
		EMSG2(_(e_loadfunc), reg->name);
	    return FAIL;
	}
    }
    return OK;
}

    int
java_enabled(int verbose)
{
    return java_link_init(DYNAMIC_JAVA_DLL, verbose) == OK;
}

#endif /* DYNAMIC_JAVA */

/* functions for converting between C strings and Java strings */

    static jobject
to_jstring(const char_u *cstring)
{
    return (*env)->NewStringUTF(env, cstring);
}

    static const char *
to_cstring(jobject jstring)
{
    return (*env)->GetStringUTFChars(env, jstring, NULL);
}

    static void
release_cstring(jclass jstring, const char *cstring)
{
    (*env)->ReleaseStringUTFChars(env, jstring, cstring);
}

    static jobject
to_jstring2(JNIEnv* _env, const char_u *cstring)
{
    return (*_env)->NewStringUTF(_env, cstring);
}

    static const char *
to_cstring2(JNIEnv* _env, jobject jstring)
{
    return (*_env)->GetStringUTFChars(_env, jstring, NULL);
}

    static void
release_cstring2(JNIEnv* _env, jclass jstring, const char *cstring)
{
    (*_env)->ReleaseStringUTFChars(_env, jstring, cstring);
}

/* an Ex command can have a range prefix, so set that */
    static void
javaV_setrange(int line1, int line2)
{
    jclass cls;
    jmethodID mid;

    javaV_PushLocalFrame(16);

    cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);

    mid = (*env)->GetStaticMethodID(env, cls, "setRangeFirstLine", "(I)V");
    (*env)->CallStaticVoidMethod(env, cls, mid, line1);

    mid = (*env)->GetStaticMethodID(env, cls, "setRangeLastLine", "(I)V");
    (*env)->CallStaticVoidMethod(env, cls, mid, line2);

    javaV_PopLocalFrame;
}

    static jobject
javaV_CreateJavaType(JNIEnv *_env, typval_T *tv)
{
    const char *string_var;
    int number_var;
#ifdef FEAT_FLOAT
    double float_var;
#endif
    list_T *list_var;
    dict_T *dict_var;
    jmethodID ctor;
    jclass cls;
    jobject ret;

    if (tv == NULL)
	return NULL;

    switch (tv->v_type)
    {
    case VAR_STRING:
	string_var = tv->vval.v_string == NULL ? "" : (char *)tv->vval.v_string;

	ret = to_jstring2(_env, string_var);
	break;
    case VAR_NUMBER:
	number_var = (int) tv->vval.v_number;

#if SIZEOF_INT <= 4
	cls = (*_env)->FindClass(_env, "java/lang/Integer");
	ctor = (*_env)->GetMethodID(_env, cls, "<init>", "(I)V");
#else
	cls = (*_env)->FindClass(_env, "java/lang/Long");
	ctor = (*_env)->GetMethodID(_env, cls, "<init>", "(J)V");
#endif
	ret = (*_env)->NewObject(_env, cls, ctor, number_var);
	break;
#ifdef FEAT_FLOAT
    case VAR_FLOAT:
	float_var = tv->vval.v_float;

	cls = (*_env)->FindClass(_env, "java/lang/Double");
	ctor = (*_env)->GetMethodID(_env, cls, "<init>", "(D)V");
	ret = (*_env)->NewObject(_env, cls, ctor, float_var);
	break;
#endif
    case VAR_LIST:
	list_var = tv->vval.v_list;

	cls = (*_env)->FindClass(_env, JVM_VIM_LIST_CLASS_NAME);
	ctor = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
					  "(J)Lvim/List;");
	ret = (*_env)->CallStaticObjectMethod(_env, cls, ctor, (jlong)list_var);
	break;
    case VAR_DICT:
	dict_var = tv->vval.v_dict;

	cls = (*_env)->FindClass(_env, JVM_VIM_DICT_CLASS_NAME);
	ctor = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
					  "(J)Lvim/Dict;");
	ret = (*_env)->CallStaticObjectMethod(_env, cls, ctor, (jlong)dict_var);
	break;
    default:
	return NULL;
    }

    return ret;
}

    static jint
javaV_ThrowException(JNIEnv *_env, const char* class_name, const char *msg)
{
    jclass cls;

    cls = (*_env)->FindClass(_env, class_name);
    return (*_env)->ThrowNew(_env, cls, msg);
}

    static int
javaV_JavaObjectToTypval(JNIEnv *_env, jobject obj, typval_T *tv)
{
    jclass cls;
    jmethodID mid;
    const char *string_var;
    int number_var;
#ifdef FEAT_FLOAT
    double float_var;
#endif
    jlong pointer_var;

    if (obj == NULL)
    {
	javaV_ThrowException(_env,
			     "java/lang/IllegalArgumentException",
			     "javaV_JavaObjectToTypval()");
	return FALSE;
    }

    cls = (*_env)->FindClass(_env, "java/lang/String");
    if ((*_env)->IsInstanceOf(_env, obj, cls))
    {
	string_var = to_cstring2(_env, obj);
	tv->v_type = VAR_STRING;
	tv->vval.v_string = vim_strsave((char_u *) string_var);
	release_cstring2(_env, obj, string_var);
	return TRUE;
    }

    cls = (*_env)->FindClass(_env, "java/lang/Long");
    if ((*_env)->IsInstanceOf(_env, obj, cls))
    {
	mid = (*_env)->GetMethodID(_env, cls, "intValue", "()I");
	number_var = (*_env)->CallIntMethod(_env, obj, mid);
	tv->v_type = VAR_NUMBER;
	tv->vval.v_number = number_var;
	return TRUE;
    }

    cls = (*_env)->FindClass(_env, "java/lang/Integer");
    if ((*_env)->IsInstanceOf(_env, obj, cls))
    {
	mid = (*_env)->GetMethodID(_env, cls, "intValue", "()I");
	number_var = (*_env)->CallIntMethod(_env, obj, mid);
	tv->v_type = VAR_NUMBER;
	tv->vval.v_number = number_var;
	return TRUE;
    }

    cls = (*_env)->FindClass(_env, "java/lang/Float");
    if ((*_env)->IsInstanceOf(_env, obj, cls))
    {
	mid = (*_env)->GetMethodID(_env, cls, "doubleValue", "()D");
	float_var = (*_env)->CallDoubleMethod(_env, obj, mid);
	tv->v_type = VAR_FLOAT;
	tv->vval.v_float = float_var;
	return TRUE;
    }
    cls = (*_env)->FindClass(_env, "java/lang/Double");
    if ((*_env)->IsInstanceOf(_env, obj, cls))
    {
	mid = (*_env)->GetMethodID(_env, cls, "doubleValue", "()D");
	float_var = (*_env)->CallDoubleMethod(_env, obj, mid);
	tv->v_type = VAR_FLOAT;
	tv->vval.v_float = float_var;
	return TRUE;
    }

    cls = (*_env)->FindClass(_env, "vim/List");
    if ((*_env)->IsInstanceOf(_env, obj, cls))
    {
	mid = (*_env)->GetMethodID(_env, cls, "getPointer", "()J");
	pointer_var = (*_env)->CallLongMethod(_env, obj, mid);
	tv->v_type = VAR_LIST;
	tv->vval.v_list = (list_T *) pointer_var;
	tv->vval.v_list->lv_refcount++;
	return TRUE;
    }

    return FALSE;
}

/* ======================================= */
/* =======   Java native methods   ======= */
/* ======================================= */

/* =======   Vim base functionality   ======= */

    JNIEXPORT void JNICALL
Java_vim_Vim__1msg(JNIEnv *_env, jclass jcls, jstring jmsg)
{
    const char *message;

    if (jmsg == NULL)
	return;
    message = to_cstring2(_env, jmsg);
    MSG(message);
    release_cstring2(_env, jmsg, message);
}

    JNIEXPORT void JNICALL
Java_vim_Vim__1emsg(JNIEnv *_env, jclass jcls, jstring jmsg)
{
    const char *message;

    if (jmsg == NULL)
	return;
    message = to_cstring2(_env, jmsg);
    EMSG(message);
    release_cstring2(_env, jmsg, message);
}

    JNIEXPORT jobject JNICALL
Java_vim_Vim__1eval(JNIEnv *_env, jclass jcls, jstring jexpr)
{
    const char *expr;
    typval_T *tv;
    jobject ret;

    expr = to_cstring2(_env, jexpr);
    tv = eval_expr((char_u *) expr, NULL);
    release_cstring2(_env, jexpr, expr);

    if (tv == NULL)
    {
	javaV_ThrowException(_env,
			     "java/lang/IllegalArgumentException",
			     "vim/Vim.eval()");
	return NULL;
    }
    else
    {
	ret =  javaV_CreateJavaType(_env, tv);
	if (ret)
	    clear_tv(tv);
	return ret;
    }
}

    JNIEXPORT void JNICALL
Java_vim_Vim__1command(JNIEnv *_env, jclass jcls, jstring jcmd)
{
    const char *cmd;

    cmd = to_cstring2(_env, jcmd);
    do_cmdline_cmd((char_u *) cmd);
    release_cstring2(_env, jcmd, cmd);
    update_screen(VALID);
}

    JNIEXPORT void JNICALL
Java_vim_Vim__1beep(JNIEnv *_env, jclass jcls)
{
    vim_beep();
}

    JNIEXPORT jobject JNICALL
Java_vim_Vim__1buffer(JNIEnv *_env, jclass jcls, jstring jarg)
{
    buf_T *b;
    int num;
    const char *arg;
    size_t arg_len;
    jclass cls;
    jmethodID mid;
    jobject object;

    arg = to_cstring2(_env, jarg);
    num = atoi(arg);
    b = NULL;

    if (num != 0) /* search by number */
    {
	for (b = firstbuf; b != NULL; b = b->b_next)
	    if (b->b_fnum == num)
		break;
    }
    else /* search by name */
    {
	arg_len = strlen(arg);

	for (b = firstbuf; b != NULL; b = b->b_next)
	{
	    if (b->b_ffname == NULL || b->b_sfname == NULL)
	    {
		if (arg_len == 0)
		    if (arg_len == 0)
			break;
	    }
	    else if (strncmp(arg, (char *)b->b_ffname, arg_len) == 0
		     || strncmp(arg, (char *)b->b_sfname, arg_len) == 0)
		break;
	}
    }

    release_cstring2(_env, jarg, arg);

    if (b == NULL)
    {
	return NULL;
    }
    else
    {
	cls = (*_env)->FindClass(_env, JVM_VIM_BUFFER_CLASS_NAME);
	mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
					 "(IJ)Lvim/Buffer;");
	object = (*_env)->CallStaticObjectMethod(_env, cls, mid,
						 b->b_fnum, (jlong)b);
	return object;
    }
}

    JNIEXPORT jobject JNICALL
Java_vim_Vim__1window(JNIEnv *_env, jclass jcls, jstring jarg)
{
    win_T *win;
    const char *arg;
    size_t arg_len;
    int n;
    jclass cls;
    jmethodID mid;
    jobject object;

    arg = to_cstring2(_env, jarg);
    arg_len = strlen(arg);

    if (strncmp(arg, "true", 4) == 0)
    {
	win = firstwin;
    }
    else if (strncmp(arg, "false", 5) == 0)
    {
	win = curwin;
    }
    else
    {
	n = atoi(arg);
	for (win = firstwin; win != NULL; win = win->w_next, --n)
	    if (n == 1)
		break;
    }

    release_cstring2(_env, jarg, arg);

    if (win == NULL)
	return NULL;
    cls = (*_env)->FindClass(_env, JVM_VIM_WINDOW_CLASS_NAME);
    mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
				     "(J)Lvim/Window;");
    object = (*_env)->CallStaticObjectMethod(_env, cls, mid, (jlong)win);
    return object;
}

    JNIEXPORT jobject JNICALL
Java_vim_Vim__1open(JNIEnv *_env, jclass jcls, jstring jfname)
{
    buf_T *b;
    const char *fname;
    jclass cls;
    jmethodID mid;
    jclass object;

    /* according to buflist_new comments, b will never be NULL */
    fname = to_cstring2(_env, jfname);
    b = buflist_new((char_u *)fname, NULL, 1L, BLN_LISTED);
    release_cstring2(_env, jfname, fname);

    cls = (*_env)->FindClass(_env, JVM_VIM_BUFFER_CLASS_NAME);
    mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
				     "(IJ)Lvim/Buffer;");
    object = (*_env)->CallStaticObjectMethod(_env, cls, mid,
					     b->b_fnum, (jlong)b);
    return object;
}

/* =======   List type   ======= */

    JNIEXPORT void JNICALL
Java_vim_List_incrementReferenceCount(JNIEnv *_env, jclass jcls, jlong pointer)
{
    list_T *l;

    l = (list_T *) pointer;
    l->lv_refcount++;
}

    JNIEXPORT void JNICALL
Java_vim_List_decrementReferenceCount(JNIEnv *_env, jclass jcls, jlong pointer)
{
    list_T *l;

    l = (list_T *) pointer;
    list_unref(l);
}

/**
 * THIS IS SUPER BROKEN RIGHT NOW!!!
 * Vim has changed to a stack based GC with abort! How should this be implemented?
 * I don't even use this anymore...
 */
    JNIEXPORT void JNICALL
Java_vim_List_setVimGCRef(JNIEnv *_env, jclass jcls, jlong pointer, jint copyID)
{
    typval_T tv;
    list_T *l;

    l = (list_T *) pointer;
    tv.v_type = VAR_LIST;
    tv.v_lock = 0;
    tv.vval.v_list = l;
    set_ref_in_item(&tv, copyID);
}

    JNIEXPORT jint JNICALL
Java_vim_List__1size(JNIEnv *_env, jclass jcls, jlong pointer)
{
    list_T *l;

    l = (list_T *) pointer;
    if (l == NULL)
	return -1;
    else
	return l->lv_len;
}

    JNIEXPORT jobject JNICALL
Java_vim_List__1get(JNIEnv *_env, jclass jcls, jlong pointer, jint index)
{
    list_T *l;
    listitem_T *li;

    l = (list_T *) pointer;
    li = list_find(l, index);
    if (li == NULL) {
	javaV_ThrowException(_env,
			     "java/lang/IndexOutOfBoundsException",
			     "vim/List.get()");
	return NULL;
    }

    return javaV_CreateJavaType(_env, &li->li_tv);
}

    JNIEXPORT void JNICALL
Java_vim_List__1set(JNIEnv *_env, jclass jcls,
		    jlong pointer, jint index, jobject item)
{
    list_T *l;
    listitem_T *li;
    typval_T v;

    l = (list_T *)pointer;
    if (l->lv_lock)
    {
	javaV_ThrowException(_env,
			     "vim/List$ListLockedException",
			     "vim/List.set()");
	return;
    }
    li = list_find(l, index);
    if (li == NULL) {
	javaV_ThrowException(_env,
			     "java/lang/IndexOutOfBoundsException",
			     "vim/List.get()");
	return;
    }
    if (javaV_JavaObjectToTypval(_env, item, &v))
    {
	clear_tv(&li->li_tv);
	copy_tv(&v, &li->li_tv);
	clear_tv(&v);
    }
}

    JNIEXPORT void JNICALL
Java_vim_List__1remove(JNIEnv *_env, jclass jcls, jlong pointer, jint index)
{
    list_T *l;
    listitem_T *li;

    l = (list_T *)pointer;
    if (l->lv_lock)
    {
	javaV_ThrowException(_env,
			     "vim/List$ListLockedException",
			     "vim/List.remove()");
	return;
    }

    li = list_find(l, index);
    if (li == NULL)
    {
	javaV_ThrowException(_env,
			     "java/lang/IndexOutOfBoundsException",
			     "vim/List.remove()");
	return;
    }
    list_remove(l, li, li);
    clear_tv(&li->li_tv);
    vim_free(li);
}

    JNIEXPORT void JNICALL
Java_vim_List__1add(JNIEnv *_env, jclass jcls, jlong pointer, jobject item)
{
    list_T *l;
    typval_T v;

    l = (list_T *)pointer;
    if (l->lv_lock)
    {
	javaV_ThrowException(_env,
			     "vim/List$ListLockedException",
			     "vim/List.add()");
	return;
    }
    if (javaV_JavaObjectToTypval(_env, item, &v))
    {
	list_append_tv(l, &v);
	clear_tv(&v);
    }
}

    JNIEXPORT void JNICALL
Java_vim_List__1insert(JNIEnv *_env, jclass jcls,
		       jlong pointer, jobject item, jint index)
{
    list_T *l;
    listitem_T *li;
    typval_T v;

    l = (list_T *)pointer;
    if (l->lv_lock)
    {
	javaV_ThrowException(_env,
			     "vim/List$ListLockedException",
			     "vim/List.insert()");
	return;
    }
    if (index < l->lv_len)
    {
	li = list_find(l, index);
	if (li == NULL)
	{
	    javaV_ThrowException(_env,
				 "java/lang/IndexOutOfBoundsException",
				 "vim/List.insert()");
	    return;
	}
    }
    else
    {
	javaV_ThrowException(_env,
			     "java/lang/IndexOutOfBoundsException",
			     "vim/List.insert()");
	return;
    }

    if (javaV_JavaObjectToTypval(_env, item, &v))
    {
	list_insert_tv(l, &v, li);
	clear_tv(&v);
    }
}

/* =======   Dict type   ======= */

    JNIEXPORT void JNICALL
Java_vim_Dict_incrementReferenceCount(JNIEnv *_env, jclass jcls, jlong pointer)
{
    dict_T *d;

    d = (dict_T *) pointer;
    d->dv_refcount++;
}

    JNIEXPORT void JNICALL
Java_vim_Dict_decrementReferenceCount(JNIEnv *_env, jclass jcls, jlong pointer)
{
    dict_T *d;

    d = (dict_T *) pointer;
    dict_unref(d);
}

/**
 * THIS IS SUPER BROKEN RIGHT NOW!!!
 * Vim has changed to a stack based GC with abort! How should this be implemented?
 * I don't even use this anymore...
 */
    JNIEXPORT void JNICALL
Java_vim_Dict_setVimGCRef(JNIEnv *_env, jclass jcls, jlong pointer, jint copyID)
{
    typval_T tv;
    dict_T *d;

    d = (dict_T *) pointer;
    tv.v_type = VAR_DICT;
    tv.v_lock = 0;
    tv.vval.v_dict = d;
    set_ref_in_item(&tv, copyID);
}

    JNIEXPORT jlong JNICALL
Java_vim_Dict__1size(JNIEnv *_env, jclass jcls, jlong pointer)
{
    dict_T *d;

    d = (dict_T *) pointer;
    if (d == NULL)
	return -1;
    else
	return d->dv_hashtab.ht_used;
}

    JNIEXPORT jobject JNICALL
Java_vim_Dict__1get(JNIEnv *_env, jclass jcls, jlong pointer, jstring jkey)
{
    dict_T *d;
    const char *key;
    dictitem_T *di;
    jobject ret;

    d = (dict_T *) pointer;
    if (d == NULL)
	return NULL;
    key = to_cstring2(_env, jkey);
    di = dict_find(d, (char_u *) key, -1);
    if (di)
    {
	ret = javaV_CreateJavaType(_env, &di->di_tv);
    }
    else
    {
	ret = NULL;
    }

    release_cstring2(_env, jkey, key);
    return ret;
}

    JNIEXPORT void JNICALL
Java_vim_Dict__1put(JNIEnv *_env, jclass jcls,
		    jlong pointer, jstring jkey, jobject jvalue)
{
    dict_T *d;
    const char *key;
    dictitem_T *di;
    typval_T v;

    d = (dict_T *) pointer;
    if (d == NULL)
	return;
    if (d->dv_lock)
    {
	javaV_ThrowException(_env,
			     "vim/Dict$DictLockedException",
			     "vim/Dict.put()");
	return;
    }
    key = to_cstring2(_env, jkey);
    di = dict_find(d, (char_u *) key, -1);
    if (di == NULL) /* new key */
    {
	di = dictitem_alloc((char_u *)key);
	if (di == NULL)
	{
	    release_cstring2(_env, jkey, key);
	    return;
	}
	if (dict_add(d, di) == FAIL)
	{
	    vim_free(di);
	    release_cstring2(_env, jkey, key);
	    return;
	}
    }
    else
	clear_tv(&di->di_tv);
    if (javaV_JavaObjectToTypval(_env, jvalue, &v))
    {
	copy_tv(&v, &di->di_tv);
	clear_tv(&v);
    }

    release_cstring2(_env, jkey, key);
}

    JNIEXPORT void JNICALL
Java_vim_Dict__1remove(JNIEnv *_env, jclass jcls, jlong pointer, jstring jkey)
{
    dict_T *d;
    const char *key;
    dictitem_T *di;
    hashitem_T *hi;

    d = (dict_T *) pointer;
    if (d == NULL)
	return;
    if (d->dv_lock)
    {
	javaV_ThrowException(_env,
			     "vim/Dict$DictLockedException",
			     "vim/Dict.put()");
	return;
    }
    key = to_cstring2(_env, jkey);
    di = dict_find(d, (char_u *) key, -1);
    if (di)
    {
	hi = hash_find(&d->dv_hashtab, di->di_key);
	hash_remove(&d->dv_hashtab, hi);
	dictitem_free(di);
    }
    release_cstring2(_env, jkey, key);
}

    JNIEXPORT jlong JNICALL
Java_vim_Dict_00024DictIterator_getHashTableArrayPointer(
    JNIEnv *_env, jclass jcls, jlong pointer)
{
    dict_T *d;

    d = (dict_T *) pointer;
    if (d == NULL)
	return 0;
    return (jlong)d->dv_hashtab.ht_array;
}

    JNIEXPORT jlong JNICALL
Java_vim_Dict_00024DictIterator_getNextHashItemPointer(
    JNIEnv *_env, jclass jcls, jlong pointer)
{
    hashitem_T *hi;

    hi = (hashitem_T *) pointer;
    if (hi == NULL)
	return 0;
    while (HASHITEM_EMPTY(hi))
	hi++;
    return (jlong)hi;
}

    JNIEXPORT jstring JNICALL
Java_vim_Dict_00024DictIterator_getKeyOfHashItem(
    JNIEnv *_env, jclass jcls, jlong pointer)
{
    hashitem_T *hi;

    hi = (hashitem_T *) pointer;
    if (hi == NULL)
	return NULL;

    return to_jstring2(_env, hi->hi_key);
}

    JNIEXPORT jobject JNICALL
Java_vim_Dict_00024DictIterator_getValueOfHashItem(
    JNIEnv *_env, jclass jcls, jlong pointer)
{
    hashitem_T *hi;
    dictitem_T *di;

    hi = (hashitem_T *) pointer;
    if (hi == NULL)
	return NULL;
    di = dict_lookup(hi);
    return javaV_CreateJavaType(_env, &di->di_tv);
}

    JNIEXPORT jlong JNICALL
Java_vim_Dict_00024DictIterator_incrementHashItemPointer(
    JNIEnv *_env, jclass jcls, jlong pointer)
{
    hashitem_T *hi;
    hi = (hashitem_T *) pointer;
    hi++;
    return (jlong)hi;
}

/* =======   Buffer type   ======= */

JNIEXPORT void JNICALL
Java_vim_Buffer__1setAsCurrent(JNIEnv *_env, jclass jcls, jlong pointer)
{
    buf_T *b;

    b = (buf_T*)pointer;
    set_curbuf(b, DOBUF_SPLIT);
}

    JNIEXPORT jint JNICALL
Java_vim_Buffer__1getNumLines(JNIEnv *_env, jclass jcls, jlong pointer)
{
    buf_T *b;

    b = (buf_T*)pointer;
    return b->b_ml.ml_line_count;
}

    JNIEXPORT jstring JNICALL
Java_vim_Buffer__1getLine(JNIEnv *_env, jclass jcls, jlong pointer, jint line_number)
{
    buf_T *b;
    linenr_T n;
    const char *line;
    jobject jline;

    b = (buf_T*)pointer;
    n = (linenr_T)line_number;

    if (n > 0 && n <= b->b_ml.ml_line_count)
    {
	line = (const char *) ml_get_buf(b, n, FALSE);
	jline = to_jstring2(_env, line);
	return jline;
    }
    else
    {
	return NULL;
    }
}

    JNIEXPORT jobjectArray JNICALL
Java_vim_Buffer__1getLines(JNIEnv *_env, jclass jcls, jlong pointer,
			   jint startLineNumber, jint endLineNumber)
{
    buf_T *b;
    linenr_T i = 0;
    linenr_T num_lines;
    jobjectArray result;
    const char *line;
    jobject jline;

    b = (buf_T*)pointer;
    num_lines = b->b_ml.ml_line_count;
    if (startLineNumber < 1 || endLineNumber > num_lines
	|| startLineNumber > endLineNumber)
    {
	return NULL;
    }

    result = (*_env)->NewObjectArray(_env,
				     num_lines,
				     (*_env)->FindClass(_env, "java/lang/String"),
				     NULL);
    if (!result)
	return NULL;

    for (i = startLineNumber; i <= endLineNumber; ++i)
    {
	line = (const char *) ml_get_buf(b, i, FALSE);
	jline = to_jstring2(_env, line);
	(*_env)->SetObjectArrayElement(_env, result, i - 1, jline);
    }

    return result;
}

/* this belongs in the Vim class, but is here since it uses the above function */
    JNIEXPORT jstring JNICALL
Java_vim_Vim__1line(JNIEnv *_env, jclass jcls)
{
    return Java_vim_Buffer__1getLine(_env, jcls, (jlong)curbuf,
				     (jint)curwin->w_cursor.lnum);
}

    JNIEXPORT void JNICALL
Java_vim_Buffer__1setLine(JNIEnv *_env, jclass jcls,
			  jlong pointer, jint line_number, jstring jnew_line)
{
    buf_T *b;
    linenr_T n;
    buf_T *buf;
    const char *new_line;

    b = (buf_T*)pointer;
    n = (linenr_T)line_number;

    if (n < 1 || n > b->b_ml.ml_line_count)
    {
	javaV_ThrowAndReturn(_env, "invalid line number");
    }

    if (jnew_line == NULL) /* delete line */
    {
	buf = curbuf;
	curbuf = b;

	if (u_savedel(n, 1L) == FAIL)
	{
	    curbuf = buf;
	    javaV_ThrowAndReturn(_env, "cannot save undo information");
	}

	if (ml_delete(n, FALSE) == FAIL)
	{
	    curbuf = buf;
	    javaV_ThrowAndReturn(_env, "cannot delete line");
	}

	deleted_lines_mark(n, 1L);
	if (b == curwin->w_buffer) /* fix cursor in current window? */
	{
	    if (curwin->w_cursor.lnum >= n)
	    {
		if (curwin->w_cursor.lnum > n)
		{
		    curwin->w_cursor.lnum -= 1;
		    check_cursor_col();
		}
		else
		    check_cursor();
		changed_cline_bef_curs();
	    }
	    invalidate_botline();
	}
	curbuf = buf;
    }
    else /* update line */
    {
	buf = curbuf;
	curbuf = b;

	if (u_savesub(n) == FAIL)
	{
	    curbuf = buf;
	    javaV_ThrowAndReturn(_env, "cannot save undo information");
	}
	else
	{
	    new_line = to_cstring2(_env, jnew_line);

	    if (ml_replace(n, (char_u *)new_line, TRUE) == FAIL)
	    {
		curbuf = buf;
		release_cstring2(_env, jnew_line, new_line);
		javaV_ThrowAndReturn(_env, "cannot replace line");
	    }
	    else
		changed_bytes(n, 0);

	    release_cstring2(_env, jnew_line, new_line);
	}
	curbuf = buf;
	if (b == curwin->w_buffer)
	    check_cursor_col();
    }
}

    JNIEXPORT jstring JNICALL
Java_vim_Buffer__1getName(JNIEnv *_env, jclass jcls, jlong pointer)
{
    buf_T *b;
    char *name;

    b = (buf_T*)pointer;
    name = b->b_sfname;

    return to_jstring2(_env, name);
}

    JNIEXPORT jstring JNICALL
Java_vim_Buffer__1getFullName(JNIEnv *_env, jclass jcls, jlong pointer)
{
    buf_T *b;
    char *fname;

    b = (buf_T*)pointer;
    fname = b->b_ffname;

    return to_jstring2(_env, fname);
}

    JNIEXPORT jint JNICALL
Java_vim_Buffer_getNumber(JNIEnv *_env, jclass jcls, jlong pointer)
{
    buf_T *b;
    int num;

    b = (buf_T*)pointer;
    num = b->b_fnum;

    return num;
}

    JNIEXPORT void JNICALL
Java_vim_Buffer__1insertLine(JNIEnv *_env, jclass jcls,
			     jlong pointer, jstring jnew_line, jint pos)
{
    buf_T *b;
    buf_T *buf;
    linenr_T n;
    linenr_T last;
    const char *new_line;

    b = (buf_T *)pointer;
    last = b->b_ml.ml_line_count;

    if (pos == -1)
	n = last;
    else
	n = (linenr_T)pos;

    /* fix insertion line */
    if (n < 0)
	n = 0;
    if (n > last)
	n = last;
    /* insert */
    buf = curbuf;
    curbuf = b;

    if (u_save(n, n + 1) == FAIL)
    {
	curbuf = buf;
	javaV_ThrowAndReturn(_env, "cannot save undo information");
    }
    else
    {
	new_line = to_cstring2(_env, jnew_line);

	if (ml_append(n, (char_u *) new_line, 0, FALSE) == FAIL)
	{
	    curbuf = buf;
	    release_cstring2(_env, jnew_line, new_line);
	    javaV_ThrowAndReturn(_env, "cannot insert line");
	}
	else
	    appended_lines_mark(n, 1L);

	release_cstring2(_env, jnew_line, new_line);
    }

    curbuf = buf;
    update_screen(VALID);
}

    JNIEXPORT jobject JNICALL
Java_vim_Buffer__1next(JNIEnv *_env, jclass jcls, jlong pointer)
{
    jclass cls;
    jmethodID mid;
    jobject object;
    buf_T *b;

    b = (buf_T *)pointer;
    b = b->b_next;
    if (b == NULL)
	return NULL;

    cls = (*_env)->FindClass(_env, JVM_VIM_BUFFER_CLASS_NAME);
    mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
				     "(IJ)Lvim/Buffer;");
    object = (*_env)->CallStaticObjectMethod(_env, cls, mid,
					     b->b_fnum, (jlong)b);
    return object;
}

    JNIEXPORT jobject JNICALL
Java_vim_Buffer__1previous(JNIEnv *_env, jclass jcls, jlong pointer)
{
    jclass cls;
    jmethodID mid;
    jobject object;
    buf_T *b;

    b = (buf_T *)pointer;
    b = b->b_prev;
    if (b == NULL)
	return NULL;

    cls = (*_env)->FindClass(_env, JVM_VIM_BUFFER_CLASS_NAME);
    mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
				     "(IJ)Lvim/Buffer;");
    object = (*_env)->CallStaticObjectMethod(_env, cls, mid,
					     b->b_fnum, (jlong)b);
    return object;
}

/* =======   Window type   ======= */

    JNIEXPORT void JNICALL
Java_vim_Window__1setAsCurrent(JNIEnv *_env, jclass jcls, jlong pointer)
{
    win_T *w;

    w = (win_T *) pointer;
    win_goto(w);
}

    JNIEXPORT jobject JNICALL
Java_vim_Window__1getBuffer(JNIEnv *_env, jclass jcls, jlong pointer)
{
    jclass cls;
    jmethodID mid;
    jobject object;
    win_T *w;
    buf_T *b;

    w = (win_T *)pointer;
    b = w->w_buffer;
    if (b == NULL)
	return NULL;

    cls = (*_env)->FindClass(_env, JVM_VIM_BUFFER_CLASS_NAME);
    mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
				     "(IJ)Lvim/Buffer;");
    object = (*_env)->CallStaticObjectMethod(_env, cls, mid,
					     b->b_fnum, (jlong)b);
    return object;
}

    JNIEXPORT jint JNICALL
Java_vim_Window__1getLinePos(JNIEnv *_env, jclass jcls, jlong pointer)
{
    win_T *w;

    w = (win_T *) pointer;
    return w->w_cursor.lnum;
}

    JNIEXPORT jboolean JNICALL
Java_vim_Window__1setLinePos(JNIEnv *_env, jclass jcls, jlong pointer,
			     jint line_pos)
{
    win_T *w;

    w = (win_T *) pointer;
    if (line_pos < 1 || line_pos > w->w_buffer->b_ml.ml_line_count)
	return FALSE;
    w->w_cursor.lnum = line_pos;
    update_screen(VALID);
    return TRUE;
}

    JNIEXPORT jint JNICALL
Java_vim_Window__1getColPos(JNIEnv *_env, jclass jcls, jlong pointer)
{
    win_T *w;

    w = (win_T *) pointer;
    return w->w_cursor.col + 1;
}

    JNIEXPORT void JNICALL
Java_vim_Window__1setColPos(JNIEnv *_env, jclass jcls, jlong pointer,
			    jint col_pos)
{
    win_T *w;

    w = (win_T *) pointer;
    w->w_cursor.col = col_pos - 1;
    update_screen(VALID);
}

    JNIEXPORT jint JNICALL
Java_vim_Window__1getWidth(JNIEnv *_env, jclass jcls, jlong pointer)
{
#ifdef FEAT_VERTSPLIT
    win_T *w;

    w = (win_T *) pointer;
    return W_WIDTH(w);
#else
    return 0;
#endif
}

    JNIEXPORT void JNICALL
Java_vim_Window__1setWidth(JNIEnv *_env, jclass jcls, jlong pointer, jint width)
{
#ifdef FEAT_VERTSPLIT
    win_T *win;
    win_T *w;

    w = (win_T *) pointer;
    win = curwin;
#ifdef FEAT_GUI
    need_mouse_correct = TRUE;
#endif
    curwin = w;
    win_setwidth(width);
    curwin = win;
#endif
}

    JNIEXPORT jint JNICALL
Java_vim_Window__1getHeight(JNIEnv *_env, jclass jcls, jlong pointer)
{
    win_T *w;

    w = (win_T *) pointer;
    return w->w_height;
}

    JNIEXPORT void JNICALL
Java_vim_Window__1setHeight(JNIEnv *_env, jclass jcls, jlong pointer, jint height)
{
    win_T *win;
    win_T *w;

    w = (win_T *) pointer;
    win = curwin;
#ifdef FEAT_GUI
    need_mouse_correct = TRUE;
#endif
    curwin = w;
    win_setheight(height);
    curwin = win;
}

    JNIEXPORT jobject JNICALL
Java_vim_Window__1next(JNIEnv *_env, jclass jcls, jlong pointer)
{
    win_T *w;
    win_T *win;
    jclass cls;
    jmethodID mid;
    jobject object;

    w = (win_T *) pointer;
    win = w->w_next;

    if (win == NULL)
    {
	return NULL;
    }
    else
    {
	cls = (*_env)->FindClass(_env, JVM_VIM_WINDOW_CLASS_NAME);
	mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
					 "(J)Lvim/Window;");
	object = (*_env)->CallStaticObjectMethod(_env, cls, mid, (jlong)win);
	return object;
    }
}

    JNIEXPORT jobject JNICALL
Java_vim_Window__1previous(JNIEnv *_env, jclass jcls, jlong pointer)
{
    win_T *w;
    win_T *win;
    jclass cls;
    jmethodID mid;
    jobject object;

    w = (win_T *) pointer;
    win = w->w_prev;

    if (win == NULL)
    {
	return NULL;
    }
    else
    {
	cls = (*_env)->FindClass(_env, JVM_VIM_WINDOW_CLASS_NAME);
	mid = (*_env)->GetStaticMethodID(_env, cls, "getOrCreate",
					 "(J)Lvim/Window;");
	object = (*_env)->CallStaticObjectMethod(_env, cls, mid, (jlong)win);
	return object;
    }
}


/* =======   method tables   ======= */

static JNINativeMethod Vim_methods[] =
{
    /* name, signature, function pointer */
    {"_msg",     "(Ljava/lang/String;)V",		   Java_vim_Vim__1msg},
    {"_emsg",    "(Ljava/lang/String;)V",		   Java_vim_Vim__1emsg},
    {"_eval",    "(Ljava/lang/String;)Ljava/lang/Object;", Java_vim_Vim__1eval},
    {"_command", "(Ljava/lang/String;)V",		   Java_vim_Vim__1command},
    {"_beep",    "()V",					   Java_vim_Vim__1beep},
    {"_buffer",  "(Ljava/lang/String;)Lvim/Buffer;",	   Java_vim_Vim__1buffer},
    {"_window",  "(Ljava/lang/String;)Lvim/Window;",	   Java_vim_Vim__1window},
    {"_line",    "()Ljava/lang/String;",		   Java_vim_Vim__1line},
    {"_open",    "(Ljava/lang/String;)Lvim/Buffer;",	   Java_vim_Vim__1open}
};

static JNINativeMethod List_methods[] =
{
    /* name, signature, function pointer */
    {"incrementReferenceCount",	"(J)V",	 Java_vim_List_incrementReferenceCount},
    {"decrementReferenceCount",	"(J)V",	 Java_vim_List_decrementReferenceCount},
    {"setVimGCRef",		"(JI)V", Java_vim_List_setVimGCRef},
    {"_size",	"(J)I",			    Java_vim_List__1size},
    {"_get",	"(JI)Ljava/lang/Object;",   Java_vim_List__1get},
    {"_set",	"(JILjava/lang/Object;)V",  Java_vim_List__1set},
    {"_remove",	"(JI)V",		    Java_vim_List__1remove},
    {"_add",	"(JLjava/lang/Object;)V",   Java_vim_List__1add},
    {"_insert",	"(JLjava/lang/Object;I)V",  Java_vim_List__1insert},
};

static JNINativeMethod Dict_methods[] =
{
    /* name, signature, function pointer */
    {"incrementReferenceCount",	"(J)V",	 Java_vim_Dict_incrementReferenceCount},
    {"decrementReferenceCount",	"(J)V",	 Java_vim_Dict_decrementReferenceCount},
    {"setVimGCRef",		"(JI)V", Java_vim_Dict_setVimGCRef},
    {"_size",	"(J)J",					    Java_vim_Dict__1size},
    {"_get",	"(JLjava/lang/String;)Ljava/lang/Object;",  Java_vim_Dict__1get},
    {"_put",	"(JLjava/lang/String;Ljava/lang/Object;)V", Java_vim_Dict__1put},
    {"_remove",	"(JLjava/lang/String;)V",		    Java_vim_Dict__1remove},
};
static JNINativeMethod DictIterator_methods[] =
{
    {"getHashTableArrayPointer", "(J)J",		  Java_vim_Dict_00024DictIterator_getHashTableArrayPointer},
    {"getNextHashItemPointer",   "(J)J",		  Java_vim_Dict_00024DictIterator_getNextHashItemPointer},
    {"getKeyOfHashItem",         "(J)Ljava/lang/String;", Java_vim_Dict_00024DictIterator_getKeyOfHashItem},
    {"getValueOfHashItem",       "(J)Ljava/lang/Object;", Java_vim_Dict_00024DictIterator_getValueOfHashItem},
    {"incrementHashItemPointer", "(J)J",		  Java_vim_Dict_00024DictIterator_incrementHashItemPointer},
};

static JNINativeMethod Buffer_methods[] =
{
    /* name, signature, function pointer */
    {"_setAsCurrent","(J)V",			Java_vim_Buffer__1setAsCurrent},
    {"_getNumLines", "(J)I",			Java_vim_Buffer__1getNumLines},
    {"_getLine",     "(JI)Ljava/lang/String;",	Java_vim_Buffer__1getLine},
    {"_getLines",    "(JII)[Ljava/lang/String;",Java_vim_Buffer__1getLines},
    {"_setLine",     "(JILjava/lang/String;)V",	Java_vim_Buffer__1setLine},
    {"_getName",     "(J)Ljava/lang/String;",   Java_vim_Buffer__1getName},
    {"_getFullName", "(J)Ljava/lang/String;",   Java_vim_Buffer__1getFullName},
    {"getNumber",    "(J)I",			Java_vim_Buffer_getNumber},
    {"_insertLine",  "(JLjava/lang/String;I)V",	Java_vim_Buffer__1insertLine},
    {"_next",	     "(J)Lvim/Buffer;",		Java_vim_Buffer__1next},
    {"_previous",    "(J)Lvim/Buffer;",		Java_vim_Buffer__1previous}
};

static JNINativeMethod Window_methods[] =
{
    /* name, signature, function pointer */
    {"_setAsCurrent",	"(J)V",		    Java_vim_Window__1setAsCurrent},
    {"_getBuffer",	"(J)Lvim/Buffer;",  Java_vim_Window__1getBuffer},
    {"_getLinePos",	"(J)I",		    Java_vim_Window__1getLinePos},
    {"_setLinePos",	"(JI)Z",	    Java_vim_Window__1setLinePos},
    {"_getColPos",	"(J)I",		    Java_vim_Window__1getColPos},
    {"_setColPos",	"(JI)V",	    Java_vim_Window__1setColPos},
    {"_getWidth",	"(J)I",		    Java_vim_Window__1getWidth},
    {"_setWidth",	"(JI)V",	    Java_vim_Window__1setWidth},
    {"_getHeight",	"(J)I",		    Java_vim_Window__1getHeight},
    {"_setHeight",	"(JI)V",	    Java_vim_Window__1setHeight},
    {"_next",		"(J)Lvim/Window;",  Java_vim_Window__1next},
    {"_previous",	"(J)Lvim/Window;",  Java_vim_Window__1previous}
};

/* =======   Interface   ======= */

    static int
java_isopen(void)
{
    return jvm != NULL || env != NULL;
}

    static int
java_init_register_native_methods(void)
{
    jclass cls;
    int ret;

    /* vim.Vim */
    cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
    if (!cls)
    {
	EMSG2(_("Failed to find JVM class: %s"), JVM_VIM_CLASS_NAME);
	return FAIL;
    }
    ret = (*env)->RegisterNatives(env, cls, Vim_methods,
				  sizeof(Vim_methods) / sizeof(Vim_methods[0]));
    if (ret < 0)
    {
	EMSG2(_("Failed to register method table: %s"), JVM_VIM_CLASS_NAME);
	return FAIL;
    }

    /* vim.List */
    cls = (*env)->FindClass(env, JVM_VIM_LIST_CLASS_NAME);
    if (!cls)
    {
	EMSG2(_("Failed to find JVM class: %s"), JVM_VIM_LIST_CLASS_NAME);
	return FAIL;
    }
    ret = (*env)->RegisterNatives(env, cls, List_methods,
				  sizeof(List_methods) / sizeof(List_methods[0]));
    if (ret < 0)
    {
	EMSG2(_("Failed to register method table: %s"), JVM_VIM_LIST_CLASS_NAME);
	return FAIL;
    }

    /* vim.Dict */
    cls = (*env)->FindClass(env, JVM_VIM_DICT_CLASS_NAME);
    if (!cls)
    {
	EMSG2(_("Failed to find JVM class: %s"), JVM_VIM_DICT_CLASS_NAME);
	return FAIL;
    }
    ret = (*env)->RegisterNatives(env, cls, Dict_methods,
				  sizeof(Dict_methods) / sizeof(Dict_methods[0]));
    if (ret < 0)
    {
	EMSG2(_("Failed to register method table: %s"), JVM_VIM_DICT_CLASS_NAME);
	return FAIL;
    }

    /* vim.Dict.Iterator */
    cls = (*env)->FindClass(env, JVM_VIM_DICT_ITERATOR_CLASS_NAME);
    if (!cls)
    {
	EMSG2(_("Failed to find JVM class: %s"), JVM_VIM_DICT_ITERATOR_CLASS_NAME);
	return FAIL;
    }
    ret = (*env)->RegisterNatives(env, cls, DictIterator_methods,
				  sizeof(DictIterator_methods) / sizeof(DictIterator_methods[0]));
    if (ret < 0)
    {
	EMSG2(_("Failed to register method table: %s"), JVM_VIM_DICT_ITERATOR_CLASS_NAME);
	return FAIL;
    }

    /* vim.Buffer */
    cls = (*env)->FindClass(env, JVM_VIM_BUFFER_CLASS_NAME);
    if (!cls)
    {
	EMSG2(_("Failed to find JVM class: %s"), JVM_VIM_BUFFER_CLASS_NAME);
	return FAIL;
    }
    ret = (*env)->RegisterNatives(env, cls, Buffer_methods,
				  sizeof(Buffer_methods) / sizeof(Buffer_methods[0]));
    if (ret < 0)
    {
	EMSG2(_("Failed to register method table: %s"), JVM_VIM_BUFFER_CLASS_NAME);
	return FAIL;
    }

    /* vim.Window */
    cls = (*env)->FindClass(env, JVM_VIM_WINDOW_CLASS_NAME);
    if (!cls)
    {
	EMSG2(_("Failed to find JVM class: %s"), JVM_VIM_WINDOW_CLASS_NAME);
	return FAIL;
    }
    ret = (*env)->RegisterNatives(env, cls, Window_methods,
				  sizeof(Window_methods) / sizeof(Window_methods[0]));
    if (ret < 0)
    {
	EMSG2(_("Failed to register method table: %s"), JVM_VIM_WINDOW_CLASS_NAME);
	return FAIL;
    }

    return OK;
}

    static int
java_init(void)
{
    JavaVMOption options[1];
    JavaVMInitArgs vm_args;
    int ret;
    char_u *buf;
    unsigned len;
    jclass cls;
    jmethodID mid;
    jboolean init_ret;

    if (!java_isopen())
    {
#ifdef DYNAMIC_JAVA
	if (!java_enabled(TRUE))
	{
	    EMSG(_("Java library cannot be loaded."));
	    return FAIL;
	}
#endif
    }
    else
    {
        return OK;
    }

#if JAVA_VER < 11
    EMSG(_("Version of Java does not have a JNI interface"));
    return FAIL;
#elif JAVA_VER == 11
    vm_args.version = JNI_VERSION_1_1;
#elif JAVA_VER == 12
    vm_args.version = JNI_VERSION_1_2;
#elif JAVA_VER <= 15
    vm_args.version = JNI_VERSION_1_4;
#else
    vm_args.version = JNI_VERSION_1_6;
#endif

    /* build string to set JVM classpath */
    len = (unsigned)strlen(JVM_CLASSPATH_OPTION_PREFIX);
    len += (unsigned)strlen(p_javacp);
    len += 1;
    buf = lalloc(len, TRUE);
    buf[0] = '\0';
    vim_strcat(buf, (char_u *)JVM_CLASSPATH_OPTION_PREFIX, len);
    vim_strcat(buf, p_javacp, len);
    options[0].optionString = buf;

    vm_args.nOptions = 1;
    vm_args.options = options;
    vm_args.ignoreUnrecognized = 0;

    ret = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args);
    vim_free(buf);
    if (ret < 0)
    {
	jvm = NULL;
	env = NULL;

	EMSG(_("Java VM could not be created."));
	return FAIL;
    }

    if (!java_init_register_native_methods())
    {
	EMSG(_("Failed to register native methods with JNI"));
	return FAIL;
    }

    cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
    mid = (*env)->GetStaticMethodID(env, cls, "init", "()Z");
    init_ret = (*env)->CallStaticBooleanMethod(env, cls, mid);
    if (init_ret == JNI_FALSE)
    {
	EMSG(_("Failed Java init() after creating JVM"));
	return FAIL;
    }

    return OK;
}

    void
java_end(void)
{
    jclass cls;
    jmethodID mid;

    if (java_isopen())
    {
	/* signal to Java interpreters that we are exiting to give them a
	 * chance to do cleanup. this is mainly for plugins that spawn
	 * background threads */
	cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
	mid = (*env)->GetStaticMethodID(env, cls, "onExit", "()V");
	(*env)->CallStaticVoidMethod(env, cls, mid);

	/* According to the latest JNI documentation (Java 7 at the time this
	 * code was created), VM unloading is not supported, so there is no
	 * real point to even calling this.
	 *
	 * Additionally, stray threads in the JVM will cause this to hang as
	 * this function waits for all background threads to terminate.
	 */
	/*(*jvm)->DestroyJavaVM(jvm);*/
	jvm = NULL;
	env = NULL;
#ifdef DYNAMIC_JAVA
	end_dynamic_java();
#endif
    }
}

    void
ex_java(exarg_T *eap)
{
    char *script;
    char *s;
    jclass cls;
    jmethodID mid;
    jobject jarg;
    jobject ret;
    const char *ret_string;

    if (java_init() == FAIL)
	return;

    javaV_PushLocalFrame(16);

    script = (char *) script_get(eap, eap->arg);
    if (!eap->skip)
    {
	s = (script) ? script : (char *) eap->arg;
	javaV_setrange(eap->line1, eap->line2);

	jarg = to_jstring(s);
	cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
	mid = (*env)->GetStaticMethodID(env, cls, "ex_java",
		"(Ljava/lang/String;)Ljava/lang/String;");
	ret = (*env)->CallStaticObjectMethod(env, cls, mid, jarg);
	if (ret)
	{
	    ret_string = to_cstring(ret);
	    MSG(ret_string);
	    release_cstring(ret, ret_string);
	}
    }
    if (script != NULL)
	vim_free(script);

    javaV_PopLocalFrame;
}

    void
ex_javafile(exarg_T *eap)
{
    jobject jfile;
    jclass cls;
    jmethodID mid;
    jobject ret;
    const char *ret_string;

    if (java_init() == FAIL)
	return;

    javaV_PushLocalFrame(16);

    if (!eap->skip)
    {
	javaV_setrange(eap->line1, eap->line2);

	jfile = to_jstring(eap->arg);
	cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
	mid = (*env)->GetStaticMethodID(env, cls, "ex_javafile",
		"(Ljava/lang/String;)Ljava/lang/String;");
	ret = (*env)->CallStaticObjectMethod(env, cls, mid, jfile);
	if (ret)
	{
	    ret_string = to_cstring(ret);
	    MSG(ret_string);
	    release_cstring(ret, ret_string);
	}
    }

    javaV_PopLocalFrame;
}

    void
ex_javarepl(exarg_T *eap)
{
    jobject repl;
    jclass cls;
    jmethodID mid;

    if (java_init() == FAIL)
	return;

    javaV_PushLocalFrame(16);

    if (!eap->skip)
    {
	repl = to_jstring(eap->arg);
	cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
	mid = (*env)->GetStaticMethodID(env, cls, "ex_javarepl",
		"(Ljava/lang/String;)V");
	(*env)->CallStaticVoidMethod(env, cls, mid, repl);
    }

    javaV_PopLocalFrame;
}

    void
java_buffer_free(buf_T *buf)
{
    jclass cls;
    jmethodID mid;

    if (!java_isopen())
	return;

    cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
    mid = (*env)->GetStaticMethodID(env, cls, "markBufferInvalid", "(I)V");
    (*env)->CallStaticVoidMethod(env, cls, mid, buf->b_fnum);
}

    void
java_window_free(win_T *win)
{
    jclass cls;
    jmethodID mid;

    if (!java_isopen())
	return;

    cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
    mid = (*env)->GetStaticMethodID(env, cls, "markWindowInvalid", "(J)V");
    (*env)->CallStaticVoidMethod(env, cls, mid, (jlong)win);
}

    void
do_javaeval(char_u *str, typval_T *rettv)
{
    jclass cls;
    jmethodID mid;
    jobject jstr;
    jobject ret;

    if (java_init() == FAIL)
	return;

    javaV_PushLocalFrame(16);

    jstr = to_jstring(str);

    cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
    mid = (*env)->GetStaticMethodID(env, cls, "do_javaeval",
				    "(Ljava/lang/String;)Ljava/lang/Object;");
    ret = (*env)->CallStaticObjectMethod(env, cls, mid, jstr);
    javaV_JavaObjectToTypval(env, ret, rettv);

    javaV_PopLocalFrame;
}

/**
 * THIS IS SUPER BROKEN RIGHT NOW!!!
 * Vim has changed to a stack based GC with abort! How should this be implemented?
 * I don't even use this anymore...
 */
    void
set_ref_in_java(int copyID)
{
    jclass cls;
    jmethodID mid;

    if (!java_isopen())
	return;

    cls = (*env)->FindClass(env, JVM_VIM_CLASS_NAME);
    mid = (*env)->GetStaticMethodID(env, cls, "setRefInCollections", "(I)V");
    (*env)->CallStaticVoidMethod(env, cls, mid, copyID);
}

    void
java_list_purge(list_T *l)
{
    jclass cls;
    jmethodID mid;

    if (!java_isopen())
	return;

    cls = (*env)->FindClass(env, JVM_VIM_LIST_CLASS_NAME);
    mid = (*env)->GetStaticMethodID(env, cls, "purge", "(J)V");
    (*env)->CallStaticVoidMethod(env, cls, mid, (jlong)l);
}
    void
java_dict_purge(dict_T *d)
{
    jclass cls;
    jmethodID mid;

    if (!java_isopen())
	return;

    cls = (*env)->FindClass(env, JVM_VIM_DICT_CLASS_NAME);
    mid = (*env)->GetStaticMethodID(env, cls, "purge", "(J)V");
    (*env)->CallStaticVoidMethod(env, cls, mid, (jlong)d);
}

#endif /* defined(FEAT_JAVA) || defined(PROTO) */
