package com.github.forax.framework.mapper;

import jdk.jshell.execution.Util;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JSONWriter {

  private static final ClassValue<List<Generator>> PROPERTIES_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected List<Generator> computeValue(Class<?> type) {
      var beanInfo = Utils.beanInfo(type);
      var properties = beanInfo.getPropertyDescriptors();
      return Arrays.stream(properties)
              .filter(prop -> !prop.getName().equals("class"))
              .<Generator>map( property -> {
                var key = '"' + property.getName() + "\": ";
                var getter = property.getReadMethod();
                return (writer, bean) -> key + writer.toJSON(Utils.invokeMethod(bean, getter));
              })
              .toList();
    }
  };

  public String toJSON(Object o) {
    return switch(o){
      case null -> "null";
      case Boolean b -> "" + b;
      case Integer i -> "" + i;
      case Double d -> "" + d;
      case String s -> '"' + s + '"';
      case Object obj -> {
        var fun = map.get(obj.getClass());
        if(fun != null){
          yield fun.apply(obj);
        }
        var generators = PROPERTIES_CLASS_VALUE.get(obj.getClass());
        yield generators.stream()
                .map(generator -> generator.generate(this, obj))
                .collect(Collectors.joining(", ", "{","}"));
      }
    };
  }

  private static Object extractValue(PropertyDescriptor property, Object object){
    var getter = property.getReadMethod();
    return Utils.invokeMethod(object, getter);
  }

  private interface Generator {
    String generate(JSONWriter writer, Object bean);
  }

  private final HashMap<Class<?>, Function<Object, String>> map = new HashMap<>();
  public <T> void configure(Class<T> type, Function<T,String> fun){
    Objects.requireNonNull(type);
    Objects.requireNonNull(fun);
    var result = map.putIfAbsent(type, o -> fun.apply(type.cast(o))); //TODO use compose or andThen
    if(result != null){
      throw new IllegalStateException("configuration for " + type.getName() + " already exists");
    }
  }

}
