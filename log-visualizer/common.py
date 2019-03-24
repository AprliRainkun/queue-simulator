import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

series_metrics = ['in', 'out', 'current', 'existing', 'creating', 'averageLatency']
histogram_metrics = ['timeToServe', 'timeOfInvoke']
should_smooth = ['in', 'out']


def read_tables(folder):
    if folder.endswith('/'):
        folder = folder[:-1]
    snapshots = pd.read_csv(folder + '/snapshots.csv')
    activations = pd.read_csv(folder + '/activations.csv')
    return format_data(snapshots, activations)


def format_data(*dfs):
    def convert(df):
        df['elapse'] = pd.to_timedelta(df['elapse'], unit='ns')
        # rolling window smooth requires a index of time deltas
        return df.set_index('elapse')

    return map(convert, dfs)


def plot_time_series(df, figsize=(12, 7)):
    fig, axes = plt.subplots(2, 3, figsize=figsize)
    fig.subplots_adjust(hspace=0.4)

    for metric, ax in zip(series_metrics, axes.flatten()):
        x = df.index.values / 1e6  # convert to ms
        y = df[metric]
        if metric in should_smooth:
            y = y.rolling('1s').sum()
        ax.plot(x, y)

        ax.grid(axis='both')
        ax.set_title(metric)
        ax.set_xlabel('ms')
    plt.show()
    return fig


def plot_histogram(df, figsize=(10, 4)):
    fig, axes = plt.subplots(1, 2, figsize=figsize)
    fig.subplots_adjust(wspace=0.3)

    for metric, ax in zip(histogram_metrics, axes.flatten()):
        y = df[metric] / 1e6  # convert to ms
        _, bins, _ = ax.hist(
            y, bins=100, range=(y.min(), np.percentile(y, 95)),
            color='orange'
        )
        ax.grid(axis='x')
        ax.set_title(metric)
        ax.set_xlabel('ms')
        ax.set_ylabel('number')

        right = ax.twinx()
        right.hist(
            y, bins=bins, density=True, cumulative=True,
            histtype='step', linewidth=2
        )
        right.grid(axis='y')
        right.set_ylabel('likelihood')
    plt.show()
    return fig
