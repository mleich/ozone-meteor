package eu.stratosphere.sopremo.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class InputSuggestion {
	private SimilarityMeasure similarityMeasure = new Levensthein();

	private int maxSuggestions = Integer.MAX_VALUE;

	private double minSimilarity = 0;

	public InputSuggestion() {
	}

	public int getMaxSuggestions() {
		return this.maxSuggestions;
	}

	public double getMinSimilarity() {
		return this.minSimilarity;
	}

	public SimilarityMeasure getSimilarityMeasure() {
		return this.similarityMeasure;
	}

	public void setMaxSuggestions(final int maxSuggestions) {
		if (maxSuggestions < 1)
			throw new IllegalArgumentException("maxSuggestions must >= 1");

		this.maxSuggestions = maxSuggestions;
	}

	public void setMinSimilarity(final double minSimilarity) {
		if (minSimilarity < 0 || minSimilarity > 1)
			throw new IllegalArgumentException("minSimilarity must be in [0; 1]");

		this.minSimilarity = minSimilarity;
	}

	public void setSimilarityMeasure(final SimilarityMeasure similarityMeasure) {
		if (similarityMeasure == null)
			throw new NullPointerException("similarityMeasure must not be null");

		this.similarityMeasure = similarityMeasure;
	}

	public <T> List<String> suggest(final CharSequence input, Collection<?> possibleValues) {
		final List<String> suggestions = new ArrayList<String>();
		for (final Pair<String, Double> entry : this.suggestWithProbability(input, possibleValues))
			suggestions.add(entry.getKey());
		return suggestions;
	}

	public List<Pair<String, Double>> suggestWithProbability(final CharSequence input,
			Collection<?> possibleValues) {
		final List<Pair<String, Double>> list = new ArrayList<Pair<String, Double>>();

		// calculate similarity values for each possible value
		for (final Object possibleValue : possibleValues) {
			final double similarity = this.similarityMeasure.getSimilarity(input, possibleValue.toString(),
				this.minSimilarity);
			if (similarity >= this.minSimilarity)
				list.add(new ImmutablePair<String, Double>(possibleValue.toString(), similarity));
		}

		// sort largest to smallest
		Collections.sort(list, new Comparator<Pair<String, Double>>() {
			@Override
			public int compare(final Pair<String, Double> o1, final Pair<String, Double> o2) {
				return Double.compare(o2.getValue(), o1.getValue());
			}
		});

		return list.size() > this.maxSuggestions ? list.subList(0, this.maxSuggestions) : list;
	}

	public InputSuggestion withMaxSuggestions(final int maxSuggestions) {
		this.setMaxSuggestions(maxSuggestions);
		return this;
	}

	public InputSuggestion withMinSimilarity(final double minSimilarity) {
		this.setMinSimilarity(minSimilarity);
		return this;
	}

	public InputSuggestion withSimilarityMeasure(final SimilarityMeasure similarityMeasure) {
		this.setSimilarityMeasure(similarityMeasure);
		return this;
	}

	public static class Levensthein implements SimilarityMeasure {
		@Override
		public double getSimilarity(final CharSequence input, final CharSequence possibleValue,
				final double minSimilarity) {
			final int length = Math.max(input.length(), possibleValue.length());
			final int threshold = (int) Math.ceil(length * (1 - minSimilarity));
			final int distance = StringUtils.getLevenshteinDistance(input, possibleValue, threshold);
			if (distance == -1)
				return 0;
			return 1 - (double) distance / length;
		}
	}

	public static interface SimilarityMeasure {
		public double getSimilarity(CharSequence input, CharSequence possibleValue, double minSimilarity);
	}
}
