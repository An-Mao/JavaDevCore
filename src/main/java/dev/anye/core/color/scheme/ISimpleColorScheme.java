package dev.anye.core.color.scheme;

import dev.anye.core.color.IStateColor;

public interface ISimpleColorScheme {
	IStateColor border();
	IStateColor text();
	IStateColor background();
}