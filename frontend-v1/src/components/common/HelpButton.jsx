import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronDown, CircleHelp, Sparkles, X } from 'lucide-react';
import RoutePath from '../../core/constants/routes.constant';
import './HelpButton.css';

const faqs = [
  {
    question: 'What can I do with SmartCRM?',
    answer:
      'Manage customers, track leads and opportunities, organize follow-ups, and keep your team’s sales activity in one place.',
  },
  {
    question: 'How do I get started?',
    answer:
      'Create an account or start a free trial, then add your customers and invite your team to begin managing your pipeline.',
  },
  {
    question: 'Can I track leads and sales opportunities?',
    answer:
      'Yes. Use the Leads and Opportunities sections to follow progress, update stages, and keep important sales details together.',
  },
  {
    question: 'Can my team see reports and dashboards?',
    answer:
      'SmartCRM includes dashboards and reports to help your team review pipeline activity and business performance.',
  },
  {
    question: 'Can I change my plan later?',
    answer:
      'You can compare the available plans on the Plans page and choose the option that fits your team.',
  },
  {
    question: 'Where can I get more help?',
    answer:
      'Visit our Contact page and send us a message. We’ll be happy to help with your questions.',
  },
];

const HelpButton = () => {
  const buttonRef = useRef(null);
  const dialogRef = useRef(null);

  const openHelp = () => {
    const button = buttonRef.current;
    const dialog = dialogRef.current;

    if (button && dialog && !dialog.open) {
      const buttonRect = button.getBoundingClientRect();
      const right = Math.max(12, window.innerWidth - buttonRect.right);
      const maxHeight = Math.max(
        180,
        Math.min(560, window.innerHeight - 24, buttonRect.top - 12),
      );

      dialog.style.right = `${right}px`;
      dialog.style.bottom = `${window.innerHeight - buttonRect.top + 8}px`;
      dialog.style.setProperty('--app-help-dialog-max-height', `${maxHeight}px`);
      dialog.showModal();
    }
  };

  return (
    <>
      <div className="app-help-button-area">
        <button
          ref={buttonRef}
          type="button"
          className="app-help-button"
          onClick={openHelp}
          aria-haspopup="dialog"
          aria-label="Open help and frequently asked questions"
        >
          <span className="app-help-button__icon" aria-hidden="true">
            <CircleHelp size={19} />
          </span>
          <span>Help</span>
        </button>
      </div>

      <dialog
        ref={dialogRef}
        className="app-help-dialog"
        aria-labelledby="app-help-title"
        onClick={(event) => {
          if (event.target === dialogRef.current) {
            dialogRef.current.close();
          }
        }}
      >
        <div className="app-help-dialog__header">
          <div className="app-help-dialog__header-icon" aria-hidden="true">
            <Sparkles size={22} />
          </div>
          <div className="app-help-dialog__heading">
            <span className="app-help-dialog__eyebrow">SMARTCRM SUPPORT</span>
            <h2 id="app-help-title">How can we help?</h2>
            <p>Quick answers to frequently asked questions.</p>
          </div>
          <button
            type="button"
            className="app-help-dialog__close"
            onClick={() => dialogRef.current?.close()}
            aria-label="Close help"
            autoFocus
          >
            <X size={19} />
          </button>
        </div>

        <div className="app-help-dialog__content">
          <div className="app-help-dialog__intro">
            <CircleHelp size={17} aria-hidden="true" />
            <div>
              <strong>Quick answers to frequently asked questions</strong>
              <span>Choose a question to see its answer.</span>
            </div>
          </div>

          <div className="app-help-faqs">
            {faqs.map(({ question, answer }) => (
              <details className="app-help-faq" key={question}>
                <summary>
                  <span>{question}</span>
                  <ChevronDown
                    className="app-help-faq__chevron"
                    size={18}
                    aria-hidden="true"
                  />
                </summary>
                <p>{answer}</p>
              </details>
            ))}
          </div>
        </div>

        <div className="app-help-dialog__footer">
          <div>
            <strong>Still need a hand?</strong>
            <span>Our team is happy to help.</span>
          </div>
          <Link
            className="app-help-dialog__contact"
            to={RoutePath.CONTACT}
            onClick={() => dialogRef.current?.close()}
          >
            Contact us
          </Link>
        </div>
      </dialog>
    </>
  );
};

export default HelpButton;
