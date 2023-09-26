package com.zhu56.apt;

import com.history.core.util.stream.Ztream;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;
import lombok.experimental.Delegate;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 基础处理器
 *
 * @author Zhu56
 * @date 2023/05/01
 */
public abstract class BaseProcessor extends AbstractProcessor {

    /**
     * 用于在编译器打印消息的组件
     */
    Messager messager;

    /**
     * 语法树
     */
    JavacTrees trees;
    /**
     * 元素工具
     */
    Elements elementUtils;

    Types types;

    /**
     * 用来构造语法树节点
     */
    @Delegate
    private TreeMaker treeMaker;

    /**
     * 用于创建标识符的对象
     */
    Names names;


    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new HashSet<>(super.getSupportedAnnotationTypes());
        SupportedAnnotations ann = this.getClass().getAnnotation(SupportedAnnotations.class);
        if (Objects.nonNull(ann)) {
            types.addAll(Ztream.of(ann.value()).map(Class::getName).set());
        }
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_21;
    }


    /**
     * 获取一些注解处理器执行处理逻辑时需要用到的一些关键对象
     *
     * @param processingEnv 处理环境
     */

    @Override
    public final synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        this.types = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    /**
     * 判断type1是否为type2子类
     *
     * @param type1 类型1
     * @param type2 类型2
     * @return boolean
     */
    public boolean isAssignable(TypeMirror type1, TypeMirror type2) {
        return types.isAssignable(types.erasure(type1), types.erasure(type2));
    }

    /**
     * 判断type1是否为clazz子类
     *
     * @param type1 类型1
     * @param clazz clazz
     * @return boolean
     */
    public boolean isAssignable(TypeMirror type1, Class<?> clazz) {
        return isAssignable(type1, elementUtils.getTypeElement(clazz.getName()).asType());
    }

    /**
     * 获取某个属性所在的类
     *
     * @param variableDecl 变量decl
     * @return {@link JCTree.JCClassDecl}
     */
    public JCTree.JCClassDecl getClassDeclForVariable(JCTree.JCVariableDecl variableDecl) {
        TreePath path = trees.getPath(elementUtils.getTypeElement(variableDecl.sym.owner.getQualifiedName().toString()));
        return Ztream.of(path).filter(t -> t instanceof JCTree.JCClassDecl).first().get(t -> (JCTree.JCClassDecl) t);
    }

    protected JCTree.JCExpression getInterfaceGenericType(JCTree.JCClassDecl jcClazz, String name, int index) {
        return Ztream.of(jcClazz.implementing)
                .filter(e -> e instanceof JCTree.JCTypeApply)
                .map(JCTree.JCTypeApply.class::cast)
                .filter(e -> e.clazz instanceof JCTree.JCIdent)
                .eq(e -> ((JCTree.JCIdent) e.clazz).name, names.fromString(name))
                .first()
                .map(JCTree.JCTypeApply::getTypeArguments)
                .get(l -> l.get(index));
    }

    protected JCTree.JCBlock Block(JCTree.JCStatement... statements) {
        return Block(0, List.from(statements));
    }

    /**
     * 添加方法
     *
     * @param jcClazz  jc clazz
     * @param jcMethod jc方法
     */
    public void addMethod(JCTree.JCClassDecl jcClazz, JCTree.JCMethodDecl jcMethod) {
        jcClazz.defs = jcClazz.defs.appendList(ListBuffer.of(jcMethod));
    }

    public JCTree.JCMethodDecl buildGetMethod(String name, Class<?> returnType, JCTree.JCBlock block) {
        return buildGetMethod(Flags.PUBLIC, name, returnType, block);
    }

    public JCTree.JCMethodDecl buildGetMethod(int modifier, String name, Class<?> returnType, JCTree.JCBlock block) {
        return MethodDef(
                Modifiers(modifier),
                names.fromString(name),
                Ident(names.fromString(returnType.getSimpleName())),
                List.nil(),
                List.nil(),
                List.nil(),
                block,
                null
        );
    }

    public JCTree.JCLiteral nullValue() {
        return Literal(TypeTag.BOT, null);
    }

    protected JCTree.JCReturn returnNull() {
        return Return(nullValue());
    }

    protected JCTree.JCFieldAccess Select(Name name, Name field) {
        return Select(Ident(name), field);
    }

    protected JCTree.JCFieldAccess Select(Name name, String field) {
        return Select(Ident(name), names.fromString(field));
    }

    protected JCTree.JCFieldAccess Select(JCTree.JCFieldAccess access, String field) {
        return Select(access, names.fromString(field));
    }

    public JCTree.JCIdent Ident(String name) {
        return Ident(names.fromString(name));
    }

    protected JCTree.JCMethodInvocation Apply(JCTree.JCExpression expr) {
        return Apply(List.nil(), expr, List.nil());
    }

    /**
     * 取一个集合的get(i)语句
     *
     * @param name 名字
     * @return {@link JCTree.JCArrayAccess}
     */
    public JCTree.JCArrayAccess IndexedI(Name name) {
        return Indexed(Ident(name), Ident(names.fromString("i")));
    }

    public JCTree.JCMethodInvocation Apply(JCTree.JCExpression fn, JCTree.JCExpression... args) {
        return Apply(List.nil(), fn, List.from(args));
    }

    /**
     * 取一个集合的get语句,指定索引
     *
     * @param name  名字
     * @param index 指数
     * @return {@link JCTree.JCArrayAccess}
     */
    public JCTree.JCArrayAccess Indexed(Name name, int index) {
        return Indexed(Ident(name), Ident(names.fromString(String.valueOf(index))));
    }

    public JCTree.JCIf If(JCTree.JCExpression cond, JCTree.JCStatement thenpart) {
        return If(cond, thenpart, null);
    }

    protected JCTree.JCBinary LT(JCTree.JCExpression expr, JCTree.JCExpression expr2) {
        return Binary(JCTree.Tag.LT, expr, expr2);
    }

    protected JCTree.JCBinary NE(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return Binary(JCTree.Tag.NE, lhs, rhs);
    }

    protected JCTree.JCBinary EQ(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return Binary(JCTree.Tag.EQ, lhs, rhs);
    }

    protected JCTree.JCBinary PLUS(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return Binary(JCTree.Tag.PLUS, lhs, rhs);
    }

    /**
     * 一个变量自增操作
     *
     * @param name 名字
     * @param rhs  园艺学会
     * @return {@link JCTree.JCAssign}
     */
    protected JCTree.JCAssign INCR(Name name, JCTree.JCExpression rhs) {
        return Assign(Ident(name), PLUS(Ident(name), rhs));
    }


    /**
     * 变量定义
     *
     * @param name    变量名称
     * @param typeTag 变量类型
     * @param init    初始化
     * @return {@link JCTree.JCVariableDecl}
     */
    protected JCTree.JCVariableDecl VarDef(Name name, TypeTag typeTag, JCTree.JCExpression init) {
        return VarDef(
                Modifiers(Flags.PARAMETER),
                name,
                TypeIdent(typeTag),
                init
        );
    }

    /**
     * 变量定义
     *
     * @param name     变量名称
     * @param typeName 类型名称
     * @param init     初始化
     * @return {@link JCTree.JCVariableDecl}
     */
    protected JCTree.JCVariableDecl VarDef(Name name, Name typeName, JCTree.JCExpression init) {
        return VarDef(
                Modifiers(Flags.PARAMETER),
                name,
                Ident(typeName),
                init
        );
    }

    protected JCTree.JCVariableDecl VarDef(Name name, JCTree.JCArrayTypeTree type, JCTree.JCExpression init) {
        return VarDef(
                Modifiers(Flags.PARAMETER),
                name,
                type,
                init
        );
    }

    protected JCTree.JCForLoop ForLoop(JCTree.JCStatement init,
                                       JCTree.JCExpression cond,
                                       JCTree.JCExpressionStatement step,
                                       JCTree.JCStatement body) {
        return ForLoop(List.of(init), cond, List.of(step), body);
    }

    /**
     * 信息
     *
     * @param format 格式
     * @param args   参数
     */
    public void info(String format, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, MessageFormat.format(format, args));
    }

    /**
     * 警告
     *
     * @param format 格式
     * @param args   参数
     */
    public void warn(String format, Object... args) {
        messager.printMessage(Diagnostic.Kind.WARNING, MessageFormat.format(format, args));
    }

    /**
     * 错误
     *
     * @param format 格式
     * @param args   参数
     */
    public void error(String format, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, MessageFormat.format(format, args));
    }
}