package dev.anye.core.math;

import dev.anye.core.exception._TargetException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ArithmeticFormula {
	private static final char[] Operators = {'+','-','*','/','^','%','!','|','&'};
	private static final List<Character> PreOperators = List.of(
			'*','/','^'
	);
	private ArithmeticFormula(){}


	/**
	 * 乘除等，基础运算。
	 * @return
	 */
	public static int md(String formula){
		List<Double> v = new ArrayList<>();
		List<Character> o = new ArrayList<>();
		String a = "";
		boolean s = false;
		for (int i = 0;i < formula.length();i++){
			char c = formula.charAt(i);
			if (c >= '0' && c <= '9'){
				a += c;
			}else {
				if (c == '.'){
					if (s) throw new _TargetException(formula);
					else s = true;
				}else {
					if (PreOperators.contains(c)) {
						o.add(c);
						v.add(Double.valueOf(a));
						a = "";
					} else throw new _TargetException(formula);
				}
			}
		}
		return 0;
	}
}
