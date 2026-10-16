import java.util.List;
import java.util.Map;


class Base {}
class OnlyExtends extends Base {}
interface Flyable {}
interface Swimmable {}
class OnlyImplements implements Flyable {}
class Both extends Base implements Flyable {}
abstract class ManyInterfaces implements Flyable, Swimmable, Runnable {}

interface Duck extends Flyable, Swimmable {}

abstract class AbstractThing extends Base {}
final class FinalThing extends Base {}

class Container<T> extends Base {}
class BoundedBox<T extends Number> extends Base {}
class Pair<K, V> implements Comparable<Pair<K, V>> {
    public int compareTo(Pair<K, V> o) { return 0; }
}
class GenericParent extends Container<Map<String, List<Integer>>> {}

abstract class Qualified extends java.util.ArrayList<String> implements java.util.function.Supplier<String> {}

class MultiLine
        extends Base
        implements Flyable,
                   Swimmable {
}

class Outer {
    static class StaticNested extends Base {}
    class Inner implements Flyable {}
}

// class LineComment extends Base {}
/* class BlockComment extends Base {} */
/*
 * class MultiLineComment extends Base {}
 */