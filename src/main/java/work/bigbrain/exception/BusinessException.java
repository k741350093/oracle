package work.bigbrain.exception;

public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
	
	private int errorCode = -1;
 
	public BusinessException(String message) {
		super(message);
	}
 
	public BusinessException(int errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
 
	public BusinessException(int errorCode, String msg, Throwable cause) {
		super(msg, cause);
		this.errorCode = errorCode;
	}
 
	public BusinessException(Throwable cause) {
	    super(cause);
	}
 
	public int getErrorCode() {
		return errorCode;
	}
 
	public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
}
