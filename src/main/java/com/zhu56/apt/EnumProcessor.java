package com.zhu56.apt;

import com.google.auto.service.AutoService;
import com.history.core.util.stream.Ztream;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.SneakyThrows;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;


/**
 * desc处理器
 *
 * @author Zhu56
 * @since 2023/05/01
 */
@SupportedAnnotations(Enum.class)
@AutoService(Processor.class)
public class EnumProcessor extends BaseProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //首先获取被DescGetter注解标记的元素
        var set = roundEnv.getElementsAnnotatedWith(Enum.class);
        Ztream.of(set).forEach(e -> {
            //获取当前元素的JCTree对象
            JCTree jcTree = trees.getTree(e);
            jcTree.accept(new TreeTranslator() {
                @Override
                @SneakyThrows
                public void visitClassDef(JCTree.JCClassDecl jcClazz) {
                    addGetDescMethod(jcClazz);
                }
            });
        });
        return true;
    }

    private void addGetDescMethod(JCTree.JCClassDecl jcClazz) {
        Name clazzName = jcClazz.getSimpleName();
        Name i = names.fromString("i");
        Name e = names.fromString("e");
        // 创建局部变量 values
        JCTree.JCVariableDecl valuesVar = VarDef(
                names.values,
                TypeArray(Ident(clazzName)),
                Apply(Select(clazzName, names.values))
        );

        // value为空则返回空
        JCTree.JCIf ifNullReturnNull = If(EQ(Ident(names.value), nullValue()), returnNull());

        // 创建循环体语句块
        JCTree.JCBlock loopBody = Block(
                //定义并初始化values
                VarDef(e, clazzName, IndexedI(names.values)),
                If(
                        Apply(Select(names.value, names.equals), Apply(Select(e, "getValue"))),
                        Return(Apply(Select(e, "getDesc")))
                )
        );

        // 创建循环语句
        JCTree.JCForLoop forLoop = ForLoop(
                VarDef(i, TypeTag.INT, Literal(0)),
                LT(Ident(i), Select(names.values, names.length)),
                Exec(INCR(i, Literal(1))),
                loopBody
        );

        JCTree.JCMethodDecl jcMethod = MethodDef(
                Modifiers(Flags.PUBLIC | Flags.STATIC),
                names.fromString("getDesc"),
                Ident(String.class.getSimpleName()),
                List.nil(),
                List.of(VarDef(
                        Modifiers(Flags.PARAMETER),
                        names.value,
                        getInterfaceGenericType(jcClazz, "EnumOption", 0),
                        null //初始化语句
                )), //参数列表
                List.nil(),
                Block(ifNullReturnNull, valuesVar, forLoop, returnNull()),
                null
        );

        addMethod(jcClazz, jcMethod);
    }
}
