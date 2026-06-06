import React from 'react';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error) {
    return {
      hasError: true,
      message: error?.message || 'Something went wrong.',
    };
  }

  componentDidCatch(error, info) {
    console.error('[Inkwell] UI recovered from error:', error, info);
  }

  reset = () => {
    this.setState({ hasError: false, message: '' });
    window.location.hash = '#/';
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <div className="app-shell" style={{ display: 'grid', placeItems: 'center', padding: 20 }}>
        <div className="modal" style={{ width: 'min(92vw, 420px)', textAlign: 'center' }}>
          <div style={{ fontSize: 42, color: 'var(--accent)', marginBottom: 12 }}>
            <i className="fa-solid fa-triangle-exclamation" />
          </div>
          <h2 style={{ margin: '0 0 8px', color: 'var(--text-1)' }}>Recovered safely</h2>
          <p style={{ color: 'var(--text-2)', lineHeight: 1.5, margin: '0 0 16px' }}>
            Inkwell blocked a bad note render so the app does not stay on a black screen.
          </p>
          {this.state.message ? (
            <small style={{ display: 'block', color: 'var(--text-3)', marginBottom: 16, wordBreak: 'break-word' }}>
              {this.state.message}
            </small>
          ) : null}
          <button className="btn btn-primary" type="button" onClick={this.reset}>
            Back to notes
          </button>
        </div>
      </div>
    );
  }
}
