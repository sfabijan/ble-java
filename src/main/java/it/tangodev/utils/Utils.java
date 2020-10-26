package it.tangodev.utils;

import java.util.List;

public class Utils {
	/**
	 * Convert a List<String> in String[] array.
	 * @param list
	 * @return
	 */
	public static String[] getStringArrayFromList(List<String> list) {
		String[] array = new String[list.size()];
		for (int i=0; i < list.size(); i++) {
			array[i] = list.get(i);
		}
		return array;
	}
}
