/* if_java.c */
int java_enabled __ARGS((int verbose));
void java_end __ARGS((void));
void ex_java __ARGS((exarg_T *eap));
void ex_javafile __ARGS((exarg_T *eap));
void ex_javarepl __ARGS((exarg_T *eap));
void java_buffer_free __ARGS((buf_T *buf));
void java_window_free __ARGS((win_T *win));
void do_javaeval __ARGS((char_u *str, typval_T *rettv));
int set_ref_in_java __ARGS((int copyID));
void java_list_purge __ARGS((list_T *l));
void java_dict_purge __ARGS((dict_T *d));
/* vim: set ft=c : */
