#!/usr/bin/env python3
"""
USB Camera Diagnostic Log Analyzer
Continuously monitors logs and compares behavior between working and test apps
"""

import subprocess
import time
import re
import json
from datetime import datetime
import os

class LogAnalyzer:
    def __init__(self):
        self.working_app_logs = []
        self.test_app_logs = []
        self.analysis_results = []
        self.is_running = False
        
    def start_log_capture(self):
        """Start capturing logs in the background"""
        print("Starting log capture...")
        self.is_running = True
        
        # Start adb logcat in background
        try:
            self.logcat_process = subprocess.Popen(
                ["adb", "logcat", "-v", "time"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1
            )
            print("Log capture started successfully")
        except Exception as e:
            print(f"Failed to start log capture: {e}")
            return False
        
        return True
    
    def stop_log_capture(self):
        """Stop log capture"""
        if hasattr(self, 'logcat_process'):
            self.logcat_process.terminate()
            self.logcat_process.wait()
        self.is_running = False
        print("Log capture stopped")
    
    def analyze_logs(self):
        """Analyze logs in real-time"""
        print("Starting log analysis...")
        
        while self.is_running:
            try:
                line = self.logcat_process.stdout.readline()
                if line:
                    self.process_log_line(line.strip())
            except KeyboardInterrupt:
                break
            except Exception as e:
                print(f"Error reading log: {e}")
                time.sleep(1)
    
    def process_log_line(self, line):
        """Process a single log line"""
        # Extract timestamp and content
        timestamp_match = re.match(r'(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})', line)
        if not timestamp_match:
            return
        
        timestamp = timestamp_match.group(1)
        
        # Check if it's from our test app
        if 'com.serenegiant.usbcameratest8' in line:
            self.test_app_logs.append({
                'timestamp': timestamp,
                'line': line,
                'type': self.classify_log_line(line)
            })
            self.analyze_test_app_log(line, timestamp)
        
        # Check if it's from working apps
        elif any(app in line for app in ['com.shenyaocn.android.usbcamerapro', 'com.shenyaocn.android.usbcamera']):
            self.working_app_logs.append({
                'timestamp': timestamp,
                'line': line,
                'type': self.classify_log_line(line)
            })
            self.analyze_working_app_log(line, timestamp)
        
        # Check for USB permission dialogs
        if 'UsbPermissionActivity' in line:
            self.analyze_permission_dialog(line, timestamp)
        
        # Check for USB device events
        if any(event in line for event in ['USB_DEVICE_ATTACHED', 'USB_DEVICE_DETACHED', 'USB_DEVICE_CONNECTED']):
            self.analyze_usb_event(line, timestamp)
    
    def classify_log_line(self, line):
        """Classify the type of log line"""
        if 'USB_DEVICE_ATTACHED' in line:
            return 'USB_ATTACH'
        elif 'USB_DEVICE_DETACHED' in line:
            return 'USB_DETACH'
        elif 'UsbPermissionActivity' in line:
            return 'PERMISSION_DIALOG'
        elif 'onConnect' in line:
            return 'USB_CONNECT'
        elif 'onCancel' in line:
            return 'USB_CANCEL'
        elif 'SecurityException' in line or 'permission denied' in line.lower():
            return 'PERMISSION_ERROR'
        elif 'crash' in line.lower() or 'fatal' in line.lower():
            return 'CRASH'
        else:
            return 'OTHER'
    
    def analyze_test_app_log(self, line, timestamp):
        """Analyze logs from our test app"""
        if 'onCancel' in line:
            print(f"[{timestamp}] ❌ TEST APP: USB permission cancelled")
            self.analysis_results.append({
                'timestamp': timestamp,
                'issue': 'USB_PERMISSION_CANCELLED',
                'app': 'test_app',
                'details': line
            })
        
        elif 'onConnect' in line:
            print(f"[{timestamp}] ✅ TEST APP: USB device connected successfully")
            self.analysis_results.append({
                'timestamp': timestamp,
                'success': 'USB_CONNECTED',
                'app': 'test_app',
                'details': line
            })
        
        elif 'SecurityException' in line:
            print(f"[{timestamp}] 🚨 TEST APP: Security exception detected")
            self.analysis_results.append({
                'timestamp': timestamp,
                'issue': 'SECURITY_EXCEPTION',
                'app': 'test_app',
                'details': line
            })
    
    def analyze_working_app_log(self, line, timestamp):
        """Analyze logs from working apps"""
        if 'onConnect' in line:
            print(f"[{timestamp}] ✅ WORKING APP: USB device connected successfully")
            self.analysis_results.append({
                'timestamp': timestamp,
                'success': 'USB_CONNECTED',
                'app': 'working_app',
                'details': line
            })
        
        elif 'UsbPermissionActivity' in line:
            print(f"[{timestamp}] 📋 WORKING APP: Permission dialog shown")
            self.analysis_results.append({
                'timestamp': timestamp,
                'event': 'PERMISSION_DIALOG_SHOWN',
                'app': 'working_app',
                'details': line
            })
    
    def analyze_permission_dialog(self, line, timestamp):
        """Analyze USB permission dialog events"""
        print(f"[{timestamp}] 🔐 PERMISSION DIALOG: {line}")
        self.analysis_results.append({
            'timestamp': timestamp,
            'event': 'PERMISSION_DIALOG',
            'details': line
        })
    
    def analyze_usb_event(self, line, timestamp):
        """Analyze USB device events"""
        print(f"[{timestamp}] 🔌 USB EVENT: {line}")
        self.analysis_results.append({
            'timestamp': timestamp,
            'event': 'USB_DEVICE_EVENT',
            'details': line
        })
    
    def generate_report(self):
        """Generate analysis report"""
        print("\n" + "="*60)
        print("USB CAMERA DIAGNOSTIC REPORT")
        print("="*60)
        
        # Count events
        test_app_events = len([r for r in self.analysis_results if r.get('app') == 'test_app'])
        working_app_events = len([r for r in self.analysis_results if r.get('app') == 'working_app'])
        
        print(f"Test App Events: {test_app_events}")
        print(f"Working App Events: {working_app_events}")
        print(f"Total Analysis Results: {len(self.analysis_results)}")
        
        # Analyze issues
        issues = [r for r in self.analysis_results if 'issue' in r]
        if issues:
            print(f"\n🚨 ISSUES DETECTED: {len(issues)}")
            for issue in issues:
                print(f"  - {issue['timestamp']}: {issue['issue']}")
        
        # Analyze successes
        successes = [r for r in self.analysis_results if 'success' in r]
        if successes:
            print(f"\n✅ SUCCESSES: {len(successes)}")
            for success in successes:
                print(f"  - {success['timestamp']}: {success['success']}")
        
        # Save detailed report
        self.save_detailed_report()
        
        print("\n" + "="*60)
    
    def save_detailed_report(self):
        """Save detailed report to file"""
        report = {
            'timestamp': datetime.now().isoformat(),
            'summary': {
                'test_app_events': len([r for r in self.analysis_results if r.get('app') == 'test_app']),
                'working_app_events': len([r for r in self.analysis_results if r.get('app') == 'working_app']),
                'total_events': len(self.analysis_results)
            },
            'analysis_results': self.analysis_results,
            'test_app_logs': self.test_app_logs[-100:],  # Last 100 logs
            'working_app_logs': self.working_app_logs[-100:]  # Last 100 logs
        }
        
        filename = f"usb_diagnostic_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(filename, 'w') as f:
            json.dump(report, f, indent=2)
        
        print(f"Detailed report saved to: {filename}")

def main():
    analyzer = LogAnalyzer()
    
    print("USB Camera Diagnostic Log Analyzer")
    print("Press Ctrl+C to stop and generate report")
    
    try:
        if analyzer.start_log_capture():
            analyzer.analyze_logs()
    except KeyboardInterrupt:
        print("\nStopping analysis...")
    finally:
        analyzer.stop_log_capture()
        analyzer.generate_report()

if __name__ == "__main__":
    main() 