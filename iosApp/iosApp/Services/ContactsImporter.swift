import Contacts
import Foundation
import TheRealDex

enum ImportError: LocalizedError {
    case denied(String)
    var errorDescription: String? {
        switch self {
        case .denied(let what): "Allow access to \(what) in Settings to import."
        }
    }
}

/// Reads the iPhone address book and merges it into the shared database (FR-060).
enum ContactsImporter {
    static func importAll() async throws -> ImportResult {
        let store = CNContactStore()
        guard try await store.requestAccess(for: .contacts) else { throw ImportError.denied("Contacts") }
        let contacts = try await Task.detached(priority: .userInitiated) { try read(store) }.value
        return try await Dex.shared.sources.importContacts(contacts: contacts)
    }

    private static func read(_ store: CNContactStore) throws -> [ImportedContact] {
        let keys: [CNKeyDescriptor] = [
            CNContactFormatter.descriptorForRequiredKeys(for: .fullName),
            CNContactEmailAddressesKey as CNKeyDescriptor,
            CNContactPhoneNumbersKey as CNKeyDescriptor,
            CNContactOrganizationNameKey as CNKeyDescriptor,
            CNContactJobTitleKey as CNKeyDescriptor,
            CNContactBirthdayKey as CNKeyDescriptor,
        ]
        var result: [ImportedContact] = []
        try store.enumerateContacts(with: CNContactFetchRequest(keysToFetch: keys)) { contact, _ in
            guard let name = CNContactFormatter.string(from: contact, style: .fullName), !name.isEmpty else { return }
            func nonEmpty(_ s: String) -> String? { s.isEmpty ? nil : s }
            result.append(
                ImportedContact(
                    externalId: contact.identifier,
                    name: name,
                    email: contact.emailAddresses.first.map { String($0.value) },
                    phone: contact.phoneNumbers.first?.value.stringValue,
                    company: nonEmpty(contact.organizationName),
                    role: nonEmpty(contact.jobTitle),
                    birthdayMonth: Int32(contact.birthday?.month ?? 0),
                    birthdayDay: Int32(contact.birthday?.day ?? 0),
                    birthdayYear: Int32(contact.birthday?.year ?? 0)
                )
            )
        }
        return result
    }
}
