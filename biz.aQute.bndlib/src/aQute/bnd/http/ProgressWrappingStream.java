package aQute.bnd.http;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

import aQute.bnd.service.progress.ProgressPlugin.Task;

public class ProgressWrappingStream extends InputStream {

	private InputStream	delegate;
	private Task		task;
	private int			size;
	private int			reported;
	private int			read;
	private long		timeout;
	private long		deadline;

	public ProgressWrappingStream(InputStream delegate, String name, int size, Task task, long timeout) {
		this.delegate = delegate;
		this.task = task;
		this.size = size;
		this.timeout = timeout == 0 ? Long.MAX_VALUE : timeout;
		this.read = 0;
		this.reported = 0;
		this.deadline = System.currentTimeMillis() + timeout;
	}

	@Override
	public int read() throws IOException {
		while (!isTimeout())
			try {
				int data = delegate.read();
				update(data == -1 ? -1 : 1);
				return data;
			} catch (SocketTimeoutException e) {
				if (task.isCanceled())
					throw new EOFException("Canceled");
			}
		throw new EOFException("Timeout");
	}

	private boolean isTimeout() throws IOException {
		if (timeout <= 0)
			return false;

		long now = System.currentTimeMillis();
		if (this.deadline < now) {
			delegate.close();
			return true;
		}
		return false;
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		while (!isTimeout())
			try {
				int count = delegate.read(buffer);
				return update(count);
			} catch (SocketTimeoutException e) {
				if (task.isCanceled())
					throw new EOFException("Canceled");
			}
		throw new EOFException("Timeout");
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		while (!isTimeout())
			try {
				int count = delegate.read(buffer, offset, length);
				return update(count);
			} catch (SocketTimeoutException e) {
				if (task.isCanceled())
					throw new EOFException("Canceled");
			}
		throw new EOFException("Timeout");
	}

	public int update(int count) throws IOException {

		if (task.isCanceled()) {
			delegate.close();
			throw new EOFException("Canceled");
		}


		this.deadline = System.currentTimeMillis() + timeout;

		if (count != -1) {
			read += count;
			int where = (50 + read * 100) / size;
			int delta = where - reported;
			if (delta > 0)
				task.worked(delta);
			this.reported = where;
		} else
			delegate.close();
		return count;
	}

	@Override
	public void close() throws IOException {
		delegate.close();
	}

}
