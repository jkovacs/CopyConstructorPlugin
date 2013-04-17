An IntelliJ IDEA plugin that adds the option to generate a copy constructor to the "Generate" menu (alt + insert), as
suggested in [IDEABKL-5546](http://youtrack.jetbrains.com/issue/IDEABKL-5546).

Roughly based on the [comparisonChainGen](https://github.com/yole/comparisonChainGen) example plugin.

It comes with experimental intentions that warn if a copy constructor may be buggy, such as when:

* The copy constructor does not copy all fields from one object to the other
* A field assignment in the copy constructor looks bogus (`this.x = copy.y;` or `this.x = constant;`)
* The copy constructor in a subclass does not invoke the copy constructor in the superclass